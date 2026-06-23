package com.harnessagent.orchestration.domain;

public enum FailureStrategy {
    CLARIFY,
    RETRY,
    FALLBACK_TO_SUPERVISOR,
    STOP
}
