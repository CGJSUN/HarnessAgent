package com.harnessagent.workspace.domain;

import java.time.Instant;
import com.harnessagent.production.snapshot.SnapshotMetadata;
import com.harnessagent.production.snapshot.SnapshotStoreType;

public record WorkspaceSnapshotReference(
        WorkspaceSnapshotReferenceType type,
        String snapshotId,
        String taskId,
        SnapshotStoreType backendType,
        String location,
        Instant createdAt) {

    public WorkspaceSnapshotReference {
        type = type == null ? WorkspaceSnapshotReferenceType.WORKSPACE : type;
        snapshotId = require(snapshotId, "snapshotId");
        taskId = taskId == null ? "" : taskId.trim();
        backendType = backendType == null ? SnapshotStoreType.NONE : backendType;
        location = location == null ? "" : location.trim();
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static WorkspaceSnapshotReference from(
            WorkspaceSnapshotReferenceType type,
            SnapshotMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("snapshot metadata is required");
        }
        return new WorkspaceSnapshotReference(
                type,
                metadata.id(),
                metadata.taskId(),
                metadata.backendType(),
                metadata.location(),
                metadata.createdAt());
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
