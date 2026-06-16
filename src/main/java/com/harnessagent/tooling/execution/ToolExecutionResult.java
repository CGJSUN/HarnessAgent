package com.harnessagent.tooling.execution;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.harnessagent.tooling.domain.ToolExecutionStatus;

public record ToolExecutionResult(
        String executionId,
        String toolId,
        ToolExecutionStatus status,
        String message,
        Map<String, Object> output,
        boolean approvalRequired,
        Map<String, Object> operationSummary) {

    public ToolExecutionResult {
        executionId = executionId == null || executionId.isBlank() ? UUID.randomUUID().toString() : executionId;
        toolId = toolId == null ? "" : toolId.trim();
        status = status == null ? ToolExecutionStatus.FAILED : status;
        message = message == null ? "" : message.trim();
        output = safeMap(output);
        operationSummary = safeMap(operationSummary);
    }

    public static ToolExecutionResult success(String toolId, Map<String, Object> output) {
        return new ToolExecutionResult(
                UUID.randomUUID().toString(),
                toolId,
                ToolExecutionStatus.SUCCEEDED,
                "Tool execution succeeded.",
                output,
                false,
                Map.of());
    }

    public static ToolExecutionResult denied(String toolId, String reason) {
        return new ToolExecutionResult(
                UUID.randomUUID().toString(),
                toolId,
                ToolExecutionStatus.DENIED,
                reason,
                Map.of(),
                false,
                Map.of());
    }

    public static ToolExecutionResult pending(String toolId, Map<String, Object> summary) {
        return new ToolExecutionResult(
                UUID.randomUUID().toString(),
                toolId,
                ToolExecutionStatus.PENDING_CONFIRMATION,
                "High-risk tool requires user confirmation or reviewer approval.",
                Map.of(),
                true,
                summary);
    }

    public static ToolExecutionResult duplicate(String toolId, ToolExecutionResult original) {
        return new ToolExecutionResult(
                UUID.randomUUID().toString(),
                toolId,
                ToolExecutionStatus.DUPLICATE,
                "Duplicate idempotent request returned the previous execution result.",
                original.output(),
                false,
                original.operationSummary());
    }

    public static ToolExecutionResult idempotencyConflict(String toolId) {
        return new ToolExecutionResult(
                UUID.randomUUID().toString(),
                toolId,
                ToolExecutionStatus.IDEMPOTENCY_CONFLICT,
                "Idempotency key was already used with different parameters.",
                Map.of(),
                false,
                Map.of());
    }

    public static ToolExecutionResult failed(String toolId, String reason) {
        return new ToolExecutionResult(
                UUID.randomUUID().toString(),
                toolId,
                ToolExecutionStatus.FAILED,
                reason,
                Map.of(),
                false,
                Map.of());
    }

    private static Map<String, Object> safeMap(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                result.put(key.trim(), value);
            }
        });
        return Map.copyOf(result);
    }
}
