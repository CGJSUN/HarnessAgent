package com.harnessagent.tooling.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ToolPendingConfirmation(
        String confirmationId,
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String toolId,
        String toolName,
        ToolSourceType sourceType,
        ToolRiskLevel riskLevel,
        ToolPendingConfirmationStatus status,
        Map<String, Object> parameters,
        Map<String, Object> sanitizedInput,
        Map<String, Object> operationSummary,
        String parameterFingerprint,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        Instant decidedAt,
        String decisionReason) {

    public ToolPendingConfirmation {
        confirmationId = defaultString(confirmationId, UUID.randomUUID().toString());
        tenantId = require(tenantId, "tenantId");
        userId = require(userId, "userId");
        agentId = require(agentId, "agentId");
        sessionId = require(sessionId, "sessionId");
        toolId = require(toolId, "toolId");
        toolName = defaultString(toolName, toolId);
        sourceType = sourceType == null ? ToolSourceType.INTERNAL : sourceType;
        riskLevel = riskLevel == null ? ToolRiskLevel.READ_ONLY : riskLevel;
        status = status == null ? ToolPendingConfirmationStatus.PENDING : status;
        parameters = safeMap(parameters);
        sanitizedInput = safeMap(sanitizedInput);
        operationSummary = safeMap(operationSummary);
        parameterFingerprint = defaultString(parameterFingerprint, "");
        idempotencyKey = defaultString(idempotencyKey, "");
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
        expiresAt = expiresAt == null ? createdAt.plusSeconds(24 * 60 * 60) : expiresAt;
        decisionReason = defaultString(decisionReason, "");
    }

    public static ToolPendingConfirmation pending(
            String tenantId,
            String userId,
            String agentId,
            String sessionId,
            ToolDefinition tool,
            Map<String, Object> parameters,
            Map<String, Object> sanitizedInput,
            Map<String, Object> operationSummary,
            String parameterFingerprint,
            String idempotencyKey) {
        Instant now = Instant.now();
        return new ToolPendingConfirmation(
                null,
                tenantId,
                userId,
                agentId,
                sessionId,
                tool.id(),
                tool.name(),
                tool.sourceType(),
                tool.riskLevel(),
                ToolPendingConfirmationStatus.PENDING,
                parameters,
                sanitizedInput,
                operationSummary,
                parameterFingerprint,
                idempotencyKey,
                now,
                now,
                now.plusSeconds(24 * 60 * 60),
                null,
                "");
    }

    public ToolPendingConfirmation withOperationSummary(Map<String, Object> nextOperationSummary) {
        return new ToolPendingConfirmation(
                confirmationId,
                tenantId,
                userId,
                agentId,
                sessionId,
                toolId,
                toolName,
                sourceType,
                riskLevel,
                status,
                parameters,
                sanitizedInput,
                nextOperationSummary,
                parameterFingerprint,
                idempotencyKey,
                createdAt,
                Instant.now(),
                expiresAt,
                decidedAt,
                decisionReason);
    }

    public ToolPendingConfirmation confirmed(String reason) {
        return decided(ToolPendingConfirmationStatus.CONFIRMED, reason);
    }

    public ToolPendingConfirmation rejected(String reason) {
        return decided(ToolPendingConfirmationStatus.REJECTED, reason);
    }

    private ToolPendingConfirmation decided(ToolPendingConfirmationStatus nextStatus, String reason) {
        Instant now = Instant.now();
        return new ToolPendingConfirmation(
                confirmationId,
                tenantId,
                userId,
                agentId,
                sessionId,
                toolId,
                toolName,
                sourceType,
                riskLevel,
                nextStatus,
                parameters,
                sanitizedInput,
                operationSummary,
                parameterFingerprint,
                idempotencyKey,
                createdAt,
                now,
                expiresAt,
                now,
                reason);
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

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
