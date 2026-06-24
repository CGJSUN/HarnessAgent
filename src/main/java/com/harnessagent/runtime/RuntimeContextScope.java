package com.harnessagent.runtime;

public record RuntimeContextScope(
        String ownerScopeId,
        String ownerId,
        String agentId,
        String sessionId,
        String runtimeUserId,
        String runtimeSessionId) {

    public static RuntimeContextScope fromOwnerScope(OwnerScope ownerScope) {
        return new RuntimeContextScope(
                PersonalRuntimeDefaults.PERSONAL_SCOPE_ID,
                ownerScope.ownerId(),
                ownerScope.agentId(),
                ownerScope.sessionId(),
                ownerScope.runtimeOwnerId(),
                ownerScope.runtimeSessionId());
    }

    public String workspaceId() {
        return OwnerScope.DEFAULT_WORKSPACE_ID;
    }

    public OwnerScope ownerScope() {
        return new OwnerScope(ownerId(), agentId, sessionId, workspaceId());
    }
}
