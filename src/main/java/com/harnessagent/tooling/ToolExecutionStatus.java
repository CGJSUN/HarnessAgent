package com.harnessagent.tooling;

public enum ToolExecutionStatus {
    SUCCEEDED,
    DENIED,
    PENDING_CONFIRMATION,
    DUPLICATE,
    IDEMPOTENCY_CONFLICT,
    FAILED
}
