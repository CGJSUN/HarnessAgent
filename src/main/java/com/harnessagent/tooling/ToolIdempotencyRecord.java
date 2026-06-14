package com.harnessagent.tooling;

public record ToolIdempotencyRecord(
        String key,
        String parameterFingerprint,
        ToolExecutionResult result) {
}
