package com.harnessagent.tooling.domain;

import com.harnessagent.runtime.PersonalRuntimeDefaults;

public record ToolPrincipal(
        String ownerScopeId,
        String ownerId,
        String agentId,
        String sessionId) {

    public ToolPrincipal {
        ownerScopeId = require(ownerScopeId, "ownerScopeId");
        ownerId = require(ownerId, "ownerId");
        agentId = require(agentId, "agentId");
        sessionId = require(sessionId, "sessionId");
    }

    public static ToolPrincipal forOwner(String ownerId, String agentId, String sessionId) {
        return new ToolPrincipal(
                PersonalRuntimeDefaults.PERSONAL_SCOPE_ID,
                ownerId,
                agentId,
                sessionId);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
