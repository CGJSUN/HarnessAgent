package com.harnessagent.workspace.domain;

import java.time.Instant;

public record PersonalWorkspaceRuntimeState(
        String ownerId,
        String agentId,
        String sessionId,
        String taskId,
        String planPath,
        WorkspaceSnapshotReference workspaceSnapshot,
        WorkspaceSnapshotReference sandboxSnapshot,
        Instant updatedAt) {

    public PersonalWorkspaceRuntimeState {
        ownerId = require(ownerId, "ownerId");
        agentId = require(agentId, "agentId");
        sessionId = require(sessionId, "sessionId");
        taskId = taskId == null ? "" : taskId.trim();
        planPath = planPath == null ? "" : planPath.trim();
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    public static PersonalWorkspaceRuntimeState empty(String ownerId, String agentId, String sessionId) {
        return new PersonalWorkspaceRuntimeState(
                ownerId,
                agentId,
                sessionId,
                "",
                "",
                null,
                null,
                Instant.now());
    }

    public PersonalWorkspaceRuntimeState withSnapshot(
            WorkspaceSnapshotReferenceType type,
            WorkspaceSnapshotReference reference,
            String nextTaskId,
            String nextPlanPath) {
        return new PersonalWorkspaceRuntimeState(
                ownerId,
                agentId,
                sessionId,
                nextTaskId == null || nextTaskId.isBlank() ? taskId : nextTaskId,
                nextPlanPath == null || nextPlanPath.isBlank() ? planPath : nextPlanPath,
                type == WorkspaceSnapshotReferenceType.WORKSPACE ? reference : workspaceSnapshot,
                type == WorkspaceSnapshotReferenceType.SANDBOX ? reference : sandboxSnapshot,
                Instant.now());
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
