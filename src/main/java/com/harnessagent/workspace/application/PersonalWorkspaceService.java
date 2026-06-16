package com.harnessagent.workspace.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.workspace.domain.PersonalWorkspaceLayout;
import com.harnessagent.workspace.domain.PersonalWorkspaceMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PersonalWorkspaceService {

    private static final String METADATA_FILE = "workspace.json";
    private static final String WORKSPACE_URI_PREFIX = "workspace://";
    private static final Map<String, String> DIRECTORY_NAMES = Map.of(
            "persona", "persona",
            "memory", "memory",
            "skills", "skills",
            "subagents", "subagents",
            "plans", "plans",
            "sessions", "sessions",
            "artifacts", "artifacts");

    private final HarnessAgentProperties properties;
    private final ObjectMapper objectMapper;

    public PersonalWorkspaceService(HarnessAgentProperties properties) {
        this(properties, new ObjectMapper().findAndRegisterModules());
    }

    PersonalWorkspaceService(HarnessAgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
    }

    public PersonalWorkspaceLayout initialize(RuntimeContextScope context) {
        Path root = rootDirectory(context);
        Map<String, Path> directories = directories(root);
        directories.values().forEach(this::createDirectory);
        Path metadataFile = root.resolve(METADATA_FILE).toAbsolutePath().normalize();
        PersonalWorkspaceMetadata metadata = metadata(context, directories);
        writeMetadata(metadataFile, metadata);
        return new PersonalWorkspaceLayout(
                root,
                metadataFile,
                directories.get("persona"),
                directories.get("memory"),
                directories.get("skills"),
                directories.get("subagents"),
                directories.get("plans"),
                directories.get("sessions"),
                directories.get("artifacts"),
                metadata);
    }

    public PersonalWorkspaceLayout layout(RuntimeContextScope context) {
        Path root = rootDirectory(context);
        Map<String, Path> directories = directories(root);
        Path metadataFile = root.resolve(METADATA_FILE).toAbsolutePath().normalize();
        return new PersonalWorkspaceLayout(
                root,
                metadataFile,
                directories.get("persona"),
                directories.get("memory"),
                directories.get("skills"),
                directories.get("subagents"),
                directories.get("plans"),
                directories.get("sessions"),
                directories.get("artifacts"),
                metadata(context, directories));
    }

    public Path resolveAuthorizedPath(RuntimeContextScope context, String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            throw new IllegalArgumentException("workspace path is required");
        }
        PersonalWorkspaceLayout layout = layout(context);
        Path root = layout.root();
        String path = requestedPath.trim();
        if (path.startsWith(WORKSPACE_URI_PREFIX)) {
            path = path.substring(WORKSPACE_URI_PREFIX.length());
        }
        Path relative = Path.of(path);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("workspace path must be relative");
        }
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("workspace path escapes personal workspace");
        }
        return resolved;
    }

    private Path rootDirectory(RuntimeContextScope context) {
        HarnessAgentProperties.AgentDefinition agentDefinition = properties.getAgents().get(context.agentId());
        String configured = agentDefinition == null ? null : agentDefinition.getWorkspace();
        Path agentRoot = configured == null || configured.isBlank()
                ? Path.of(".harness-agent/personal/workspaces/" + context.agentId())
                : Path.of(configured.trim());
        return agentRoot.resolve(context.userId()).toAbsolutePath().normalize();
    }

    private static Map<String, Path> directories(Path root) {
        Map<String, Path> directories = new LinkedHashMap<>();
        directories.put("persona", root.resolve(DIRECTORY_NAMES.get("persona")).toAbsolutePath().normalize());
        directories.put("memory", root.resolve(DIRECTORY_NAMES.get("memory")).toAbsolutePath().normalize());
        directories.put("skills", root.resolve(DIRECTORY_NAMES.get("skills")).toAbsolutePath().normalize());
        directories.put("subagents", root.resolve(DIRECTORY_NAMES.get("subagents")).toAbsolutePath().normalize());
        directories.put("plans", root.resolve(DIRECTORY_NAMES.get("plans")).toAbsolutePath().normalize());
        directories.put("sessions", root.resolve(DIRECTORY_NAMES.get("sessions")).toAbsolutePath().normalize());
        directories.put("artifacts", root.resolve(DIRECTORY_NAMES.get("artifacts")).toAbsolutePath().normalize());
        return directories;
    }

    private PersonalWorkspaceMetadata metadata(RuntimeContextScope context, Map<String, Path> directories) {
        Instant now = Instant.now();
        Map<String, String> relativeDirectories = new LinkedHashMap<>();
        directories.forEach((key, path) -> relativeDirectories.put(key, path.getFileName().toString()));
        return new PersonalWorkspaceMetadata(
                context.userId(),
                context.agentId(),
                context.userId() + "/" + context.agentId(),
                context.agentId() + ":*",
                relativeDirectories,
                now,
                now);
    }

    private void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to initialize personal workspace directory", ex);
        }
    }

    private void writeMetadata(Path metadataFile, PersonalWorkspaceMetadata metadata) {
        try {
            Files.createDirectories(metadataFile.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataFile.toFile(), metadata);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to write personal workspace metadata", ex);
        }
    }
}
