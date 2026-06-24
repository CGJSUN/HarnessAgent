package com.harnessagent.skill.application;

import com.harnessagent.security.application.AuthorizationService;
import com.harnessagent.security.application.SecurityActivityService;
import com.harnessagent.security.domain.Permission;
import com.harnessagent.security.domain.ResourceAccessPolicy;
import com.harnessagent.security.domain.ResourceType;
import com.harnessagent.security.domain.OwnerPrincipal;
import com.harnessagent.skill.domain.PersonalSkillMetadata;
import com.harnessagent.skill.domain.PersonalSkillStatus;
import com.harnessagent.skill.domain.SkillExecutionRequest;
import com.harnessagent.skill.domain.SkillExecutionResult;
import com.harnessagent.skill.domain.SkillPermissionSet;
import com.harnessagent.skill.domain.SkillRepositoryType;
import com.harnessagent.skill.domain.SkillValidationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PersonalSkillService {

    private static final long MAX_RESOURCE_BYTES = 256 * 1024;

    private final LocalSkillRepositoryAdapter localRepositoryAdapter;
    private final AuthorizationService authorizationService;
    private final SecurityActivityService securityActivityService;
    private final Map<String, PersonalSkillMetadata> skills = new ConcurrentHashMap<>();

    @Autowired
    public PersonalSkillService(
            LocalSkillRepositoryAdapter localRepositoryAdapter,
            AuthorizationService authorizationService,
            SecurityActivityService securityActivityService) {
        this.localRepositoryAdapter = localRepositoryAdapter == null
                ? new LocalSkillRepositoryAdapter()
                : localRepositoryAdapter;
        this.authorizationService = authorizationService == null
                ? new AuthorizationService()
                : authorizationService;
        this.securityActivityService = securityActivityService;
    }

    public PersonalSkillService(
            LocalSkillRepositoryAdapter localRepositoryAdapter,
            AuthorizationService authorizationService) {
        this(localRepositoryAdapter, authorizationService, null);
    }

    public List<PersonalSkillMetadata> refreshLocalRepository(OwnerPrincipal owner, Path repositoryRoot) {
        List<PersonalSkillMetadata> scanned = localRepositoryAdapter.scan(owner, repositoryRoot);
        List<PersonalSkillMetadata> merged = scanned.stream()
                .map(this::preserveExistingStatus)
                .toList();
        merged.forEach(skill -> skills.put(key(skill.ownerScopeId(), skill.ownerId(), skill.name(), skill.version()), skill));
        return merged;
    }

    public Set<SkillRepositoryType> repositoryTypes() {
        return Set.of(
                SkillRepositoryType.LOCAL,
                SkillRepositoryType.GIT,
                SkillRepositoryType.MYSQL,
                SkillRepositoryType.POSTGRESQL);
    }

    public SkillValidationResult validateLocalSkill(OwnerPrincipal owner, Path skillDirectory) {
        return localRepositoryAdapter.validate(skillDirectory);
    }

    public SkillExecutionResult execute(SkillExecutionRequest request) {
        PersonalSkillMetadata skill = resolveSkill(request)
                .orElseThrow(() -> new IllegalStateException("No enabled skill matches the request"));
        return execute(request, skill);
    }

    public Optional<SkillExecutionResult> tryExecute(SkillExecutionRequest request) {
        return resolveSkill(request)
                .map(skill -> execute(request, skill));
    }

    private SkillExecutionResult execute(SkillExecutionRequest request, PersonalSkillMetadata skill) {
        requireExecutable(request.principal(), skill);
        enforcePermissions(skill, request);
        Map<String, String> resources = loadResources(skill);
        return new SkillExecutionResult(
                skill.name(),
                skill.version(),
                resources.getOrDefault("SKILL.md", String.join("\n\n", resources.values())),
                resources,
                request.context());
    }

    public PersonalSkillMetadata enable(OwnerPrincipal owner, String skillName, String version) {
        PersonalSkillMetadata skill = find(owner.scopeId(), owner.ownerId(), skillName, version)
                .orElseThrow(() -> new IllegalStateException("A valid skill version is required before enabling"));
        requireNotLockedForChange(owner.scopeId(), owner.ownerId(), skillName, version);
        disableOtherVersions(owner.scopeId(), owner.ownerId(), skillName, version);
        return recordActivity(owner, save(activeSkill(skill)), "SKILL_ENABLED");
    }

    public PersonalSkillMetadata disable(OwnerPrincipal owner, String skillName, String version) {
        PersonalSkillMetadata skill = require(owner.scopeId(), owner.ownerId(), skillName, version);
        return recordActivity(owner, save(skill.withStatus(PersonalSkillStatus.DISABLED)), "SKILL_DISABLED");
    }

    public PersonalSkillMetadata upgrade(OwnerPrincipal owner, String skillName, String targetVersion) {
        requireNotLockedForChange(owner.scopeId(), owner.ownerId(), skillName, targetVersion);
        Optional<String> previousVersion = activeVersion(owner.scopeId(), owner.ownerId(), skillName);
        PersonalSkillMetadata target = require(owner.scopeId(), owner.ownerId(), skillName, targetVersion);
        disableOtherVersions(owner.scopeId(), owner.ownerId(), skillName, target.version());
        return recordActivity(owner, save(activeSkill(target)), "SKILL_UPGRADED", previousVersion);
    }

    public PersonalSkillMetadata rollback(OwnerPrincipal owner, String skillName, String targetVersion) {
        Optional<String> previousVersion = activeVersion(owner.scopeId(), owner.ownerId(), skillName);
        PersonalSkillMetadata target = require(owner.scopeId(), owner.ownerId(), skillName, targetVersion);
        disableOtherVersions(owner.scopeId(), owner.ownerId(), skillName, target.version());
        return recordActivity(owner, save(activeSkill(target)), "SKILL_ROLLED_BACK", previousVersion);
    }

    public PersonalSkillMetadata lock(OwnerPrincipal owner, String skillName, String version) {
        PersonalSkillMetadata target = require(owner.scopeId(), owner.ownerId(), skillName, version);
        disableOtherVersions(owner.scopeId(), owner.ownerId(), skillName, target.version());
        return recordActivity(owner, save(target.withStatus(PersonalSkillStatus.LOCKED)), "SKILL_VERSION_LOCKED");
    }

    public List<PersonalSkillMetadata> list(String ownerScopeId, String skillName) {
        return list(ownerScopeId, null, skillName);
    }

    public List<PersonalSkillMetadata> list(String ownerScopeId, String ownerId, String skillName) {
        return skills.values().stream()
                .filter(skill -> skill.ownerScopeId().equals(ownerScopeId))
                .filter(skill -> ownerId == null || ownerId.isBlank() || skill.ownerId().equals(ownerId))
                .filter(skill -> skillName == null || skillName.isBlank() || skill.name().equals(skillName))
                .sorted(Comparator
                        .comparing(PersonalSkillMetadata::name)
                        .thenComparing(PersonalSkillMetadata::version))
                .toList();
    }

    private Optional<PersonalSkillMetadata> resolveSkill(SkillExecutionRequest request) {
        String text = (request.taskIntent() + " " + request.task()).toLowerCase(java.util.Locale.ROOT);
        return skills.values().stream()
                .filter(skill -> skill.ownerScopeId().equals(request.principal().scopeId()))
                .filter(skill -> skill.ownerId().equals(request.principal().ownerId()))
                .filter(skill -> skill.status() == PersonalSkillStatus.ENABLED
                        || skill.status() == PersonalSkillStatus.LOCKED)
                .filter(skill -> skill.agentIds().isEmpty() || skill.agentIds().contains(request.agentId()))
                .filter(skill -> skill.triggers().stream()
                        .map(trigger -> trigger.toLowerCase(java.util.Locale.ROOT))
                        .anyMatch(text::contains))
                .sorted(Comparator
                        .comparing((PersonalSkillMetadata skill) -> skill.status() == PersonalSkillStatus.LOCKED)
                        .reversed()
                        .thenComparing(PersonalSkillMetadata::version).reversed())
                .findFirst();
    }

    private void requireExecutable(OwnerPrincipal principal, PersonalSkillMetadata skill) {
        authorizationService.require(
                principal,
                ResourceAccessPolicy.ownerOnly(
                        skill.ownerScopeId(),
                        skill.ownerId(),
                        ResourceType.SKILL,
                        Permission.EXECUTE),
                Permission.EXECUTE);
    }

    private static void enforcePermissions(PersonalSkillMetadata skill, SkillExecutionRequest request) {
        SkillPermissionSet permissions = skill.permissions();
        if (!permissions.tools().containsAll(request.requestedTools())) {
            throw new IllegalStateException("skill tool permission denied");
        }
        for (String file : request.requestedFiles()) {
            if (!fileAllowed(permissions.files(), file)) {
                throw new IllegalStateException("skill file permission denied");
            }
        }
        if (request.networkRequested() && !permissions.network()) {
            throw new IllegalStateException("skill network permission denied");
        }
        if (request.sandboxRequested() && !permissions.sandbox()) {
            throw new IllegalStateException("skill sandbox permission denied");
        }
        if (request.memoryRequested() && !permissions.memory()) {
            throw new IllegalStateException("skill memory permission denied");
        }
    }

    private static boolean fileAllowed(Set<String> allowedPatterns, String requestedFile) {
        if (requestedFile == null || requestedFile.isBlank()) {
            return false;
        }
        if (allowedPatterns.contains("*") || allowedPatterns.contains(requestedFile)) {
            return true;
        }
        return allowedPatterns.stream()
                .filter(pattern -> pattern.endsWith("/**"))
                .map(pattern -> pattern.substring(0, pattern.length() - 2))
                .anyMatch(requestedFile::startsWith);
    }

    private Map<String, String> loadResources(PersonalSkillMetadata skill) {
        Path sourcePath = localRepositoryAdapter.sourcePath(skill);
        Path root = sourcePath.toAbsolutePath().normalize();
        Path realRoot = realPath(root, "skill source");
        Map<String, String> resources = new LinkedHashMap<>();
        for (String resource : skill.resources()) {
            Path resourcePath = root.resolve(resource).normalize();
            if (!resourcePath.startsWith(root)) {
                throw new IllegalStateException("skill resource escapes source directory: " + resource);
            }
            if (!Files.isRegularFile(resourcePath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Unable to load skill resource: " + resource);
            }
            Path realResource = realPath(resourcePath, "skill resource");
            if (!realResource.startsWith(realRoot)) {
                throw new IllegalStateException("skill resource escapes source directory: " + resource);
            }
            if (fileTooLarge(resourcePath, MAX_RESOURCE_BYTES)) {
                throw new IllegalStateException("skill resource is too large: " + resource);
            }
            try {
                resources.put(resource, Files.readString(resourcePath));
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to load skill resource: " + resource, ex);
            }
        }
        return Map.copyOf(resources);
    }

    private static Path realPath(Path path, String label) {
        try {
            return path.toRealPath();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to resolve " + label + " path", ex);
        }
    }

    private static boolean fileTooLarge(Path path, long maxBytes) {
        try {
            return Files.size(path) > maxBytes;
        } catch (IOException ex) {
            return true;
        }
    }

    private void disableOtherVersions(String ownerScopeId, String ownerId, String skillName, String targetVersion) {
        list(ownerScopeId, ownerId, skillName).stream()
                .filter(skill -> !skill.version().equals(targetVersion))
                .forEach(skill -> save(skill.withStatus(PersonalSkillStatus.DISABLED)));
    }

    private Optional<String> activeVersion(String ownerScopeId, String ownerId, String skillName) {
        return list(ownerScopeId, ownerId, skillName).stream()
                .filter(skill -> skill.status() == PersonalSkillStatus.ENABLED
                        || skill.status() == PersonalSkillStatus.LOCKED)
                .map(PersonalSkillMetadata::version)
                .findFirst();
    }

    private PersonalSkillMetadata require(String ownerScopeId, String ownerId, String skillName, String version) {
        return find(ownerScopeId, ownerId, skillName, version)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill version: " + skillName + " " + version));
    }

    private Optional<PersonalSkillMetadata> find(String ownerScopeId, String ownerId, String skillName, String version) {
        return Optional.ofNullable(skills.get(key(ownerScopeId, ownerId, skillName, version)));
    }

    private PersonalSkillMetadata preserveExistingStatus(PersonalSkillMetadata scanned) {
        Optional<PersonalSkillMetadata> existing = find(scanned.ownerScopeId(), scanned.ownerId(), scanned.name(), scanned.version());
        if (existing.isEmpty()) {
            Optional<PersonalSkillMetadata> locked = list(scanned.ownerScopeId(), scanned.ownerId(), scanned.name()).stream()
                    .filter(skill -> skill.status() == PersonalSkillStatus.LOCKED)
                    .findFirst();
            if (locked.isPresent() && !locked.get().version().equals(scanned.version())) {
                return scanned.withStatus(PersonalSkillStatus.DISABLED);
            }
            return scanned;
        }
        return existing
                .map(current -> new PersonalSkillMetadata(
                        current.id(),
                        scanned.ownerScopeId(),
                        scanned.ownerId(),
                        scanned.name(),
                        scanned.description(),
                        scanned.version(),
                        scanned.triggers(),
                        scanned.sourceType(),
                        scanned.source(),
                        scanned.permissions(),
                        scanned.resources(),
                        scanned.agentIds(),
                        current.status(),
                        Instant.now()))
                .orElse(scanned);
    }

    private void requireNotLockedForChange(String ownerScopeId, String ownerId, String skillName, String targetVersion) {
        Optional<PersonalSkillMetadata> locked = list(ownerScopeId, ownerId, skillName).stream()
                .filter(skill -> skill.status() == PersonalSkillStatus.LOCKED)
                .findFirst();
        if (locked.isPresent() && !locked.get().version().equals(targetVersion)) {
            throw new IllegalStateException("Skill version is locked: " + locked.get().version());
        }
    }

    private static PersonalSkillMetadata activeSkill(PersonalSkillMetadata skill) {
        return skill.status() == PersonalSkillStatus.LOCKED
                ? skill
                : skill.withStatus(PersonalSkillStatus.ENABLED);
    }

    private PersonalSkillMetadata save(PersonalSkillMetadata skill) {
        PersonalSkillMetadata updated = new PersonalSkillMetadata(
                skill.id(),
                skill.ownerScopeId(),
                skill.ownerId(),
                skill.name(),
                skill.description(),
                skill.version(),
                skill.triggers(),
                skill.sourceType(),
                skill.source(),
                skill.permissions(),
                skill.resources(),
                skill.agentIds(),
                skill.status(),
                Instant.now());
        skills.put(key(updated.ownerScopeId(), updated.ownerId(), updated.name(), updated.version()), updated);
        return updated;
    }

    private PersonalSkillMetadata recordActivity(OwnerPrincipal owner, PersonalSkillMetadata skill, String action) {
        return recordActivity(owner, skill, action, Optional.empty());
    }

    private PersonalSkillMetadata recordActivity(
            OwnerPrincipal owner,
            PersonalSkillMetadata skill,
            String action,
            Optional<String> previousVersion) {
        if (securityActivityService != null) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("skillName", skill.name());
            details.put("version", skill.version());
            details.put("status", skill.status().name());
            details.put("sourceType", skill.sourceType().name());
            previousVersion.ifPresent(version -> details.put("previousVersion", version));
            securityActivityService.record(owner, ResourceType.SKILL, skill.id(), action, details);
        }
        return skill;
    }

    private static String key(String ownerScopeId, String ownerId, String skillName, String version) {
        return ownerScopeId + ":" + ownerId + ":" + skillName + ":" + version;
    }
}
