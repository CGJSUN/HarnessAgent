package com.harnessagent.tooling.persistence;

import com.harnessagent.tooling.execution.ToolExecutionResult;

public record ToolIdempotencyRecord(
        String key,
        String parameterFingerprint,
        ToolExecutionResult result) {
}
