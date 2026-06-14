package com.harnessagent.tooling;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record ToolExecutionCommand(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String toolId,
        Map<String, Object> parameters,
        Set<String> departments,
        Set<String> roles,
        boolean confirmed,
        String approvalId,
        String reviewerId,
        String idempotencyKey) {

    public ToolExecutionCommand {
        tenantId = require(tenantId, "tenantId");
        userId = require(userId, "userId");
        agentId = require(agentId, "agentId");
        sessionId = require(sessionId, "sessionId");
        toolId = require(toolId, "toolId");
        parameters = safeMap(parameters);
        departments = safeSet(departments);
        roles = safeSet(roles);
        approvalId = trimToNull(approvalId);
        reviewerId = trimToNull(reviewerId);
        idempotencyKey = trimToNull(idempotencyKey);
    }

    public ToolPrincipal principal() {
        return new ToolPrincipal(tenantId, userId, agentId, sessionId, departments, roles);
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

    private static Set<String> safeSet(Set<String> input) {
        if (input == null) {
            return Set.of();
        }
        return input.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }
}
