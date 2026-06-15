package com.harnessagent.production;

import java.time.Instant;
import java.util.UUID;

public record SnapshotMetadata(
        String id,
        String tenantId,
        String agentId,
        String sessionId,
        String taskId,
        Instant createdAt,
        SnapshotStoreType backendType,
        String location) {

    public SnapshotMetadata {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.trim();
        tenantId = require(tenantId, "tenantId");
        agentId = require(agentId, "agentId");
        sessionId = require(sessionId, "sessionId");
        taskId = taskId == null ? "" : taskId.trim();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        backendType = backendType == null ? SnapshotStoreType.NONE : backendType;
        location = location == null ? "" : location.trim();
    }

    public SnapshotMetadata withLocation(SnapshotStoreType backendType, String location) {
        return new SnapshotMetadata(id, tenantId, agentId, sessionId, taskId, createdAt, backendType, location);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
