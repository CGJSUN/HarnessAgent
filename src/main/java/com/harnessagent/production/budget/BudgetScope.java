package com.harnessagent.production.budget;

public record BudgetScope(
        String tenantId,
        String userId,
        String agentId,
        String providerId) {

    public BudgetScope {
        tenantId = require(tenantId, "tenantId");
        userId = require(userId, "userId");
        agentId = require(agentId, "agentId");
        providerId = require(providerId, "providerId");
    }

    public String key() {
        return String.join(":", tenantId, userId, agentId, providerId);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
