package com.harnessagent.tooling.activity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.harnessagent.tooling.domain.ToolExecutionStatus;
import com.harnessagent.tooling.domain.ToolRiskLevel;
import com.harnessagent.tooling.domain.ToolSourceType;

public record ToolActivityRecord(
        String id,
        Instant occurredAt,
        String ownerScopeId,
        String ownerId,
        String agentId,
        String sessionId,
        String toolId,
        String toolName,
        ToolSourceType sourceType,
        ToolRiskLevel riskLevel,
        ToolExecutionStatus status,
        Map<String, Object> sanitizedInput,
        Map<String, Object> sanitizedOutput,
        long durationMillis,
        String approvalId,
        String reviewerId,
        String idempotencyKey,
        String failureReason) {

    public ToolActivityRecord {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        ownerScopeId = ownerScopeId == null ? "" : ownerScopeId.trim();
        ownerId = ownerId == null ? "" : ownerId.trim();
        agentId = agentId == null ? "" : agentId.trim();
        sessionId = sessionId == null ? "" : sessionId.trim();
        toolId = toolId == null ? "" : toolId.trim();
        toolName = toolName == null ? "" : toolName.trim();
        sourceType = sourceType == null ? ToolSourceType.INTERNAL : sourceType;
        riskLevel = riskLevel == null ? ToolRiskLevel.READ_ONLY : riskLevel;
        status = status == null ? ToolExecutionStatus.FAILED : status;
        sanitizedInput = safeMap(sanitizedInput);
        sanitizedOutput = safeMap(sanitizedOutput);
        approvalId = approvalId == null ? "" : approvalId.trim();
        reviewerId = reviewerId == null ? "" : reviewerId.trim();
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        failureReason = failureReason == null ? "" : failureReason.trim();
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
