package com.harnessagent.orchestration.domain;

public record OrchestrationResult(
        RouteDecision decision,
        OrchestrationTrace trace) {
}
