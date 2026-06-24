package com.harnessagent.skill.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.security.domain.OwnerPrincipal;
import com.harnessagent.skill.domain.PersonalSkillMetadata;
import com.harnessagent.skill.domain.PersonalSkillStatus;
import com.harnessagent.skill.domain.SkillPermissionSet;
import com.harnessagent.skill.domain.SkillRepositoryType;
import com.harnessagent.skill.domain.SkillValidationResult;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LocalSkillRepositoryAdapter {

    static final String MANIFEST_FILE = "skill.json";
    private static final int MAX_SCAN_DEPTH = 6;
    private static final int MAX_MANIFESTS = 100;
    private static final long MAX_MANIFEST_BYTES = 64 * 1024;
    private static final long MAX_RESOURCE_BYTES = 256 * 1024;

    private final ObjectMapper objectMapper;

    public LocalSkillRepositoryAdapter() {
        this(new ObjectMapper().findAndRegisterModules());
    }

    LocalSkillRepositoryAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
    }

    public List<PersonalSkillMetadata> scan(OwnerPrincipal owner, Path repositoryRoot) {
        if (repositoryRoot == null || !Files.exists(repositoryRoot)) {
            return List.of();
        }
        try (var paths = Files.walk(repositoryRoot, MAX_SCAN_DEPTH)) {
            List<Path> skillDirectories = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().equals(MANIFEST_FILE))
                    .limit(MAX_MANIFESTS + 1L)
                    .map(Path::getParent)
                    .toList();
            if (skillDirectories.size() > MAX_MANIFESTS) {
                throw new IllegalStateException("Local skill repository contains too many manifests");
            }
            return skillDirectories.stream()
                    .map(path -> toMetadata(owner, path))
                    .flatMap(java.util.Optional::stream)
                    .sorted(Comparator
                            .comparing(PersonalSkillMetadata::name)
                            .thenComparing(PersonalSkillMetadata::version))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to scan local skill repository", ex);
        }
    }

    public SkillValidationResult validate(Path skillDirectory) {
        List<String> errors = new ArrayList<>();
        if (skillDirectory == null) {
            return new SkillValidationResult("", "", "", false, List.of("skill directory is required"));
        }
        if (Files.isSymbolicLink(skillDirectory)) {
            return new SkillValidationResult(
                    "",
                    "",
                    skillDirectory.toUri().toString(),
                    false,
                    List.of("skill directory must not be a symbolic link"));
        }
        Path manifestFile = skillDirectory.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifestFile, LinkOption.NOFOLLOW_LINKS)) {
            return new SkillValidationResult("", "", skillDirectory.toUri().toString(), false, List.of("skill.json is required"));
        }
        if (fileTooLarge(manifestFile, MAX_MANIFEST_BYTES)) {
            return new SkillValidationResult(
                    "",
                    "",
                    skillDirectory.toUri().toString(),
                    false,
                    List.of("skill.json is too large"));
        }
        SkillManifest manifest = readManifest(manifestFile);
        if (manifest.name() == null || manifest.name().isBlank()) {
            errors.add("name is required");
        }
        if (manifest.version() == null || manifest.version().isBlank()) {
            errors.add("version is required");
        }
        if (manifest.description() == null || manifest.description().isBlank()) {
            errors.add("description is required");
        }
        if (manifest.triggers() == null || manifest.triggers().isEmpty()) {
            errors.add("trigger is required");
        }
        Set<String> resources = allResources(manifest);
        if (manifest.resources() == null || manifest.resources().isEmpty()) {
            errors.add("resource is required");
        } else {
            resources.forEach(resource -> validateResource(skillDirectory, resource, errors));
        }
        if (executionSamples(manifest).isEmpty()) {
            errors.add("execution example is required");
        }
        if (manifest.permissions() != null && manifest.permissions().tools().stream()
                .anyMatch(tool -> tool == null || tool.isBlank())) {
            errors.add("tool dependency is required");
        }
        return new SkillValidationResult(
                manifest.name(),
                manifest.version(),
                skillDirectory.toUri().toString(),
                errors.isEmpty(),
                errors);
    }

    public Path sourcePath(PersonalSkillMetadata metadata) {
        if (metadata == null || metadata.source().isBlank()) {
            throw new IllegalArgumentException("skill source is required");
        }
        return Path.of(URI.create(metadata.source())).toAbsolutePath().normalize();
    }

    private java.util.Optional<PersonalSkillMetadata> toMetadata(OwnerPrincipal owner, Path skillDirectory) {
        SkillValidationResult validation = validate(skillDirectory);
        if (!validation.valid()) {
            return java.util.Optional.empty();
        }
        SkillManifest manifest = readManifest(skillDirectory.resolve(MANIFEST_FILE));
        return java.util.Optional.of(new PersonalSkillMetadata(
                null,
                owner.scopeId(),
                owner.ownerId(),
                manifest.name(),
                manifest.description(),
                manifest.version(),
                manifest.triggers(),
                SkillRepositoryType.LOCAL,
                skillDirectory.toUri().toString(),
                manifest.permissions() == null ? SkillPermissionSet.none() : manifest.permissions(),
                allResources(manifest),
                manifest.agentIds(),
                PersonalSkillStatus.ENABLED,
                Instant.now()));
    }

    private static void validateResource(Path skillDirectory, String resource, List<String> errors) {
        if (resource == null || resource.isBlank()) {
            errors.add("missing resource: " + String.valueOf(resource));
            return;
        }
        Path root = skillDirectory.toAbsolutePath().normalize();
        Path resourcePath = root.resolve(resource).normalize();
        if (!resourcePath.startsWith(root)) {
            errors.add("resource escapes skill directory: " + resource);
            return;
        }
        if (!Files.isRegularFile(resourcePath, LinkOption.NOFOLLOW_LINKS)) {
            errors.add("missing resource: " + resource);
            return;
        }
        try {
            Path realRoot = root.toRealPath();
            Path realResource = resourcePath.toRealPath();
            if (!realResource.startsWith(realRoot)) {
                errors.add("resource escapes skill directory: " + resource);
                return;
            }
        } catch (IOException ex) {
            errors.add("missing resource: " + resource);
            return;
        }
        if (fileTooLarge(resourcePath, MAX_RESOURCE_BYTES)) {
            errors.add("resource is too large: " + resource);
        }
    }

    private static boolean fileTooLarge(Path path, long maxBytes) {
        try {
            return Files.size(path) > maxBytes;
        } catch (IOException ex) {
            return true;
        }
    }

    private static Set<String> allResources(SkillManifest manifest) {
        LinkedHashSet<String> resources = new LinkedHashSet<>();
        if (manifest.resources() != null) {
            resources.addAll(manifest.resources());
        }
        if (manifest.examples() != null) {
            resources.addAll(manifest.examples());
        }
        return resources;
    }

    private static Set<String> executionSamples(SkillManifest manifest) {
        LinkedHashSet<String> examples = new LinkedHashSet<>();
        if (manifest.examples() != null) {
            examples.addAll(manifest.examples());
        }
        if (manifest.resources() != null) {
            manifest.resources().stream()
                    .filter(resource -> resource != null
                            && resource.toLowerCase(java.util.Locale.ROOT).contains("example"))
                    .forEach(examples::add);
        }
        examples.removeIf(resource -> resource == null || resource.isBlank());
        return Set.copyOf(examples);
    }

    private SkillManifest readManifest(Path manifestFile) {
        if (fileTooLarge(manifestFile, MAX_MANIFEST_BYTES)) {
            throw new IllegalArgumentException("Skill metadata is too large: " + manifestFile);
        }
        try {
            return objectMapper.readValue(manifestFile.toFile(), SkillManifest.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read skill metadata: " + manifestFile, ex);
        }
    }

    private record SkillManifest(
            String name,
            String description,
            String version,
            Set<String> triggers,
            SkillPermissionSet permissions,
            Set<String> resources,
            Set<String> examples,
            Set<String> agentIds) {
    }
}
