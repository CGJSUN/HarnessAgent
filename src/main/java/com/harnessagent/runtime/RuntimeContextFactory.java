package com.harnessagent.runtime;

import org.springframework.stereotype.Component;

@Component
public class RuntimeContextFactory {

    public RuntimeContextScope createPersonal(String ownerId, String agentId, String sessionId) {
        return create(new OwnerScope(
                PersonalRuntimeDefaults.ownerId(ownerId),
                normalize("agentId", agentId),
                normalize("sessionId", sessionId)));
    }

    public RuntimeContextScope create(OwnerScope ownerScope) {
        return RuntimeContextScope.fromOwnerScope(ownerScope);
    }

    public RuntimeContextScope create(
            String ownerScopeId, String ownerId, String agentId, String sessionId) {
        return createFromOwnerScope(ownerScopeId, ownerId, agentId, sessionId);
    }

    public RuntimeContextScope createFromOwnerScope(
            String ownerScopeId, String ownerId, String agentId, String sessionId) {
        String normalizedOwnerScopeId = normalize("ownerScopeId", PersonalRuntimeDefaults.ownerScopeId(ownerScopeId));
        String normalizedOwnerId = normalize("ownerId", ownerId);
        String normalizedAgentId = normalize("agentId", agentId);
        String normalizedSessionId = normalize("sessionId", sessionId);
        return new RuntimeContextScope(
                normalizedOwnerScopeId,
                normalizedOwnerId,
                normalizedAgentId,
                normalizedSessionId,
                "owner:" + normalizedOwnerId,
                normalizedAgentId + ":" + normalizedSessionId);
    }

    private static String normalize(String field, String value) {
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
