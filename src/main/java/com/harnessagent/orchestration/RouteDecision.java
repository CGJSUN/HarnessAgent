package com.harnessagent.orchestration;

public record RouteDecision(
        String selectedAgentId,
        double confidence,
        OrchestrationStatus status,
        String reason) {
}
