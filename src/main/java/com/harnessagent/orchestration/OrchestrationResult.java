package com.harnessagent.orchestration;

public record OrchestrationResult(
        RouteDecision decision,
        OrchestrationTrace trace) {
}
