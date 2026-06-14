package com.harnessagent.tooling;

import java.util.Set;
import java.util.stream.Collectors;

public record ToolPrincipal(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        Set<String> departments,
        Set<String> roles) {

    public ToolPrincipal {
        tenantId = require(tenantId, "tenantId");
        userId = require(userId, "userId");
        agentId = require(agentId, "agentId");
        sessionId = require(sessionId, "sessionId");
        departments = safeSet(departments);
        roles = safeSet(roles);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
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
