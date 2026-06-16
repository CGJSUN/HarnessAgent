package com.harnessagent.runtime;

import org.springframework.stereotype.Component;

@Component
public class RuntimeContextFactory {

    public RuntimeContextScope create(
            String tenantId, String userId, String agentId, String sessionId) {
        String normalizedTenantId = normalize("tenantId", PersonalRuntimeDefaults.tenantId(tenantId));
        String normalizedUserId = normalize("userId", PersonalRuntimeDefaults.ownerId(tenantId, userId));
        String normalizedAgentId = normalize("agentId", agentId);
        String normalizedSessionId = normalize("sessionId", sessionId);
        return new RuntimeContextScope(
                normalizedTenantId,
                normalizedUserId,
                normalizedAgentId,
                normalizedSessionId,
                normalizedTenantId + ":" + normalizedUserId,
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
