package com.harnessagent.production;

import java.time.Instant;

public record AgentStateEntry(
        String key,
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String scope,
        String value,
        Instant updatedAt) {

    public AgentStateEntry {
        key = require(key, "key");
        tenantId = require(tenantId, "tenantId");
        userId = require(userId, "userId");
        agentId = require(agentId, "agentId");
        sessionId = require(sessionId, "sessionId");
        scope = scope == null || scope.isBlank() ? "default" : scope.trim();
        value = value == null ? "" : value;
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
