package com.harnessagent.console.view;

public record CostUsageReport(
        String ownerScopeId,
        String agentId,
        String providerId,
        long tokenEvents,
        long estimatedTokens,
        double estimatedCost) {
}
