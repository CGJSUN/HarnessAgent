package com.harnessagent.production;

public record BudgetDecision(
        boolean allowed,
        String reason,
        long usedRequests,
        long usedTokens) {
}
