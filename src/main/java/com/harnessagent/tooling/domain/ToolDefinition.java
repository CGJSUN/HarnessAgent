package com.harnessagent.tooling.domain;

import java.time.Instant;
import java.util.UUID;
import com.harnessagent.production.config.AgentWorkloadType;

public record ToolDefinition(
        String id,
        String ownerScopeId,
        String name,
        String description,
        String ownerSystem,
        String ownerId,
        ToolSourceType sourceType,
        String sourceRef,
        ToolRiskLevel riskLevel,
        boolean mutating,
        boolean enabled,
        ToolParameterSchema parameterSchema,
        ToolOutputSchema outputSchema,
        ToolPermissionPolicy permissionPolicy,
        ToolActivityPolicy activityPolicy,
        AgentWorkloadType workloadType,
        Instant createdAt,
        Instant updatedAt) {

    public ToolDefinition {
        id = defaultString(id, UUID.randomUUID().toString());
        ownerScopeId = require(ownerScopeId, "ownerScopeId");
        name = require(name, "name");
        description = defaultString(description, "");
        ownerSystem = require(ownerSystem, "ownerSystem");
        ownerId = require(ownerId, "ownerId");
        sourceType = sourceType == null ? ToolSourceType.INTERNAL : sourceType;
        sourceRef = defaultString(sourceRef, ownerSystem);
        riskLevel = classifyRisk(riskLevel, mutating, name);
        parameterSchema = parameterSchema == null ? ToolParameterSchema.empty() : parameterSchema;
        outputSchema = outputSchema == null ? ToolOutputSchema.empty() : outputSchema;
        permissionPolicy = permissionPolicy == null ? ToolPermissionPolicy.allowAll() : permissionPolicy;
        activityPolicy = activityPolicy == null ? ToolActivityPolicy.standard() : activityPolicy;
        workloadType = workloadType == null ? AgentWorkloadType.OFFICE : workloadType;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public ToolDefinition(
            String id,
            String ownerScopeId,
            String name,
            String description,
            String ownerSystem,
            String ownerId,
            ToolSourceType sourceType,
            String sourceRef,
            ToolRiskLevel riskLevel,
            boolean mutating,
            boolean enabled,
            ToolParameterSchema parameterSchema,
            ToolPermissionPolicy permissionPolicy,
            ToolActivityPolicy activityPolicy,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                ownerScopeId,
                name,
                description,
                ownerSystem,
                ownerId,
                sourceType,
                sourceRef,
                riskLevel,
                mutating,
                enabled,
                parameterSchema,
                ToolOutputSchema.empty(),
                permissionPolicy,
                activityPolicy,
                AgentWorkloadType.OFFICE,
                createdAt,
                updatedAt);
    }

    public ToolDefinition(
            String id,
            String ownerScopeId,
            String name,
            String description,
            String ownerSystem,
            String ownerId,
            ToolSourceType sourceType,
            String sourceRef,
            ToolRiskLevel riskLevel,
            boolean mutating,
            boolean enabled,
            ToolParameterSchema parameterSchema,
            ToolPermissionPolicy permissionPolicy,
            ToolActivityPolicy activityPolicy,
            AgentWorkloadType workloadType,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                ownerScopeId,
                name,
                description,
                ownerSystem,
                ownerId,
                sourceType,
                sourceRef,
                riskLevel,
                mutating,
                enabled,
                parameterSchema,
                ToolOutputSchema.empty(),
                permissionPolicy,
                activityPolicy,
                workloadType,
                createdAt,
                updatedAt);
    }

    public ToolDefinition(
            String id,
            String ownerScopeId,
            String name,
            String description,
            String ownerSystem,
            String ownerId,
            ToolSourceType sourceType,
            String sourceRef,
            ToolRiskLevel riskLevel,
            boolean mutating,
            boolean enabled,
            ToolParameterSchema parameterSchema,
            ToolPermissionPolicy permissionPolicy,
            ToolActivityPolicy activityPolicy,
            ToolOutputSchema outputSchema,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                ownerScopeId,
                name,
                description,
                ownerSystem,
                ownerId,
                sourceType,
                sourceRef,
                riskLevel,
                mutating,
                enabled,
                parameterSchema,
                outputSchema,
                permissionPolicy,
                activityPolicy,
                AgentWorkloadType.OFFICE,
                createdAt,
                updatedAt);
    }

    public ToolDefinition withEnabled(boolean nextEnabled) {
        return new ToolDefinition(
                id,
                ownerScopeId,
                name,
                description,
                ownerSystem,
                ownerId,
                sourceType,
                sourceRef,
                riskLevel,
                mutating,
                nextEnabled,
                parameterSchema,
                outputSchema,
                permissionPolicy,
                activityPolicy,
                workloadType,
                createdAt,
                Instant.now());
    }

    private static ToolRiskLevel classifyRisk(ToolRiskLevel requested, boolean mutating, String name) {
        if (mutating || looksMutating(name)) {
            return ToolRiskLevel.HIGH_RISK;
        }
        return requested == null ? ToolRiskLevel.READ_ONLY : requested;
    }

    private static boolean looksMutating(String name) {
        if (name == null) {
            return false;
        }
        String normalized = name.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("write")
                || normalized.contains("send")
                || normalized.contains("submit")
                || normalized.contains("execute")
                || normalized.contains("create")
                || normalized.contains("update")
                || normalized.contains("delete");
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
