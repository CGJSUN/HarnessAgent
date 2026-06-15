package com.harnessagent.console;

import java.util.Map;

public record ToolConfirmationView(
        String toolId,
        String toolName,
        String sessionId,
        Map<String, Object> sanitizedInput,
        Map<String, Object> operationSummary,
        String idempotencyKey) {

    public ToolConfirmationView {
        sanitizedInput = sanitizedInput == null ? Map.of() : Map.copyOf(sanitizedInput);
        operationSummary = operationSummary == null ? Map.of() : Map.copyOf(operationSummary);
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
    }
}
