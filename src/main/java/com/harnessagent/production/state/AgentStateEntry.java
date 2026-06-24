package com.harnessagent.production.state;

import java.time.Instant;

public record AgentStateEntry(
        String key,
        String ownerScopeId,
        String ownerId,
        String agentId,
        String sessionId,
        String scope,
        String value,
        Instant updatedAt) {

    public AgentStateEntry {
        key = require(key, "key");
        ownerScopeId = require(ownerScopeId, "ownerScopeId");
        ownerId = require(ownerId, "ownerId");
        agentId = require(agentId, "agentId");
        sessionId = require(sessionId, "sessionId");
        scope = scope == null || scope.isBlank() ? "default" : scope.trim();
        value = value == null ? "" : value;
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    public String ownerScopeId() {
        return ownerScopeId;
    }

    public String ownerId() {
        return ownerId;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
