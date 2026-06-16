package com.harnessagent.workspace.domain;

import java.time.Instant;
import java.util.Map;

public record PersonalWorkspaceMetadata(
        String ownerId,
        String agentId,
        String workspaceId,
        String sessionNamespace,
        Map<String, String> directories,
        Instant createdAt,
        Instant updatedAt) {

    public PersonalWorkspaceMetadata {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId is required");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        if (sessionNamespace == null || sessionNamespace.isBlank()) {
            throw new IllegalArgumentException("sessionNamespace is required");
        }
        directories = directories == null ? Map.of() : Map.copyOf(directories);
        createdAt = createdAt == null ? Instant.EPOCH : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }
}
