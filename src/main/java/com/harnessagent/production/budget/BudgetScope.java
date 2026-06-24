package com.harnessagent.production.budget;

import com.harnessagent.runtime.PersonalRuntimeDefaults;

public record BudgetScope(
        String ownerScopeId,
        String ownerId,
        String agentId,
        String providerId) {

    public BudgetScope {
        ownerScopeId = require(ownerScopeId, "ownerScopeId");
        ownerId = require(ownerId, "ownerId");
        agentId = require(agentId, "agentId");
        providerId = require(providerId, "providerId");
    }

    public String key() {
        return String.join(":", ownerScopeId, ownerId, agentId, providerId);
    }

    public static BudgetScope forOwner(String ownerId, String agentId, String providerId) {
        return new BudgetScope(PersonalRuntimeDefaults.PERSONAL_SCOPE_ID, ownerId, agentId, providerId);
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
