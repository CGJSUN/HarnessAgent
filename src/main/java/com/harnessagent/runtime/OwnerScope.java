package com.harnessagent.runtime;

public record OwnerScope(String ownerId, String agentId, String sessionId, String workspaceId) {

    public static final String DEFAULT_WORKSPACE_ID = "personal";

    public OwnerScope {
        ownerId = requireSegment(ownerId, "ownerId");
        agentId = requireSegment(agentId, "agentId");
        sessionId = requireSegment(sessionId, "sessionId");
        workspaceId = requireSegment(workspaceId, "workspaceId");
    }

    public OwnerScope(String ownerId, String agentId, String sessionId) {
        this(ownerId, agentId, sessionId, DEFAULT_WORKSPACE_ID);
    }

    public static OwnerScope of(String ownerId, String agentId, String sessionId) {
        return new OwnerScope(ownerId, agentId, sessionId);
    }

    public String runtimeOwnerId() {
        return "owner:" + ownerId;
    }

    public String runtimeSessionId() {
        return agentId + ":" + sessionId;
    }

    private static String requireSegment(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.contains(":")) {
            throw new IllegalArgumentException(field + " must not contain ':'");
        }
        return normalized;
    }
}
