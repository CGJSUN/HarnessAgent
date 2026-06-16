package com.harnessagent.orchestration.domain;

public record RouteDecision(
        String selectedAgentId,
        double confidence,
        OrchestrationStatus status,
        String reason) {
}
