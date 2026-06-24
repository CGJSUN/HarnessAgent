package com.harnessagent.tooling.execution;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.harnessagent.runtime.PersonalRuntimeDefaults;
import com.harnessagent.tooling.domain.ToolPrincipal;

public record ToolExecutionCommand(
        String ownerScopeId,
        String ownerId,
        String agentId,
        String sessionId,
        String toolId,
        Map<String, Object> parameters,
        boolean confirmed,
        String idempotencyKey) {

    public ToolExecutionCommand {
        ownerScopeId = require(ownerScopeId, "ownerScopeId");
        ownerId = require(ownerId, "ownerId");
        agentId = require(agentId, "agentId");
        sessionId = require(sessionId, "sessionId");
        toolId = require(toolId, "toolId");
        parameters = safeMap(parameters);
        idempotencyKey = trimToNull(idempotencyKey);
    }

    public ToolExecutionCommand(
            String ownerScopeId,
            String ownerId,
            String agentId,
            String sessionId,
            String toolId,
            Map<String, Object> parameters,
            Set<String> ignoredOwnerHints,
            Set<String> ignoredGroupHints,
            boolean confirmed,
            String ignoredApprovalId,
            String ignoredReviewerId,
            String idempotencyKey) {
        this(ownerScopeId, ownerId, agentId, sessionId, toolId, parameters, confirmed, idempotencyKey);
    }

    public ToolPrincipal principal() {
        return new ToolPrincipal(ownerScopeId, ownerId, agentId, sessionId);
    }

    public static ToolExecutionCommand forOwner(
            String ownerId,
            String agentId,
            String sessionId,
            String toolId,
            Map<String, Object> parameters,
            boolean confirmed,
            String idempotencyKey) {
        return new ToolExecutionCommand(
                PersonalRuntimeDefaults.PERSONAL_SCOPE_ID,
                ownerId,
                agentId,
                sessionId,
                toolId,
                parameters,
                confirmed,
                idempotencyKey);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, Object> safeMap(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                result.put(key.trim(), value);
            }
        });
        return Map.copyOf(result);
    }
}
