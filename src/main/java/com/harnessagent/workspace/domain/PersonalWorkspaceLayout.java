package com.harnessagent.workspace.domain;

import java.nio.file.Path;
import java.util.Map;

public record PersonalWorkspaceLayout(
        Path root,
        Path metadataFile,
        Path personaDirectory,
        Path memoryDirectory,
        Path skillsDirectory,
        Path subagentsDirectory,
        Path plansDirectory,
        Path sessionsDirectory,
        Path artifactsDirectory,
        PersonalWorkspaceMetadata metadata) {

    public PersonalWorkspaceLayout {
        if (root == null) {
            throw new IllegalArgumentException("root is required");
        }
        if (metadataFile == null) {
            throw new IllegalArgumentException("metadataFile is required");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        root = root.toAbsolutePath().normalize();
        metadataFile = metadataFile.toAbsolutePath().normalize();
        personaDirectory = normalize(root, personaDirectory);
        memoryDirectory = normalize(root, memoryDirectory);
        skillsDirectory = normalize(root, skillsDirectory);
        subagentsDirectory = normalize(root, subagentsDirectory);
        plansDirectory = normalize(root, plansDirectory);
        sessionsDirectory = normalize(root, sessionsDirectory);
        artifactsDirectory = normalize(root, artifactsDirectory);
    }

    public Map<String, Path> directories() {
        return Map.of(
                "persona", personaDirectory,
                "memory", memoryDirectory,
                "skills", skillsDirectory,
                "subagents", subagentsDirectory,
                "plans", plansDirectory,
                "sessions", sessionsDirectory,
                "artifacts", artifactsDirectory);
    }

    private static Path normalize(Path root, Path path) {
        if (path == null) {
            throw new IllegalArgumentException("workspace directory is required");
        }
        return path.toAbsolutePath().normalize();
    }
}
