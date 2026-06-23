package com.harnessagent.orchestration.domain;

public enum OrchestrationStatus {
    PLANNED,
    ROUTED,
    EXECUTED,
    BACKGROUND_RUNNING,
    BACKGROUND_COMPLETED,
    HANDOFF,
    ESCALATED,
    BLOCKED
}
