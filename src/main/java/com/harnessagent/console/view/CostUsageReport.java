package com.harnessagent.console.view;

public record CostUsageReport(
        String tenantId,
        String agentId,
        String providerId,
        long tokenEvents,
        long estimatedTokens,
        double estimatedCost) {
}
