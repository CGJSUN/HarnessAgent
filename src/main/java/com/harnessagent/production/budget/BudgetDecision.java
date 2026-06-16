package com.harnessagent.production.budget;

public record BudgetDecision(
        boolean allowed,
        String reason,
        long usedRequests,
        long usedTokens) {
}
