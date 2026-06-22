package com.harnessagent.console.view;

import java.util.Map;

public record ToolConfirmationView(
        String confirmationId,
        String toolId,
        String toolName,
        String sessionId,
        String riskLevel,
        String status,
        Map<String, Object> sanitizedInput,
        Map<String, Object> operationSummary,
        String idempotencyKey) {

    public ToolConfirmationView {
        confirmationId = confirmationId == null ? "" : confirmationId.trim();
        riskLevel = riskLevel == null ? "" : riskLevel.trim();
        status = status == null ? "" : status.trim();
        sanitizedInput = sanitizedInput == null ? Map.of() : Map.copyOf(sanitizedInput);
        operationSummary = operationSummary == null ? Map.of() : Map.copyOf(operationSummary);
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
    }
}
