package com.harnessagent.tooling.domain;

import java.time.Instant;
import java.util.UUID;
import com.harnessagent.production.config.AgentWorkloadType;

public record ToolDefinition(
        String id,
        String tenantId,
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
        ToolAuditPolicy auditPolicy,
        AgentWorkloadType workloadType,
        Instant createdAt,
        Instant updatedAt) {

    public ToolDefinition {
        id = defaultString(id, UUID.randomUUID().toString());
        tenantId = require(tenantId, "tenantId");
        name = require(name, "name");
        description = defaultString(description, "");
        ownerSystem = require(ownerSystem, "ownerSystem");
        ownerId = require(ownerId, "ownerId");
        sourceType = sourceType == null ? ToolSourceType.INTERNAL : sourceType;
        sourceRef = defaultString(sourceRef, ownerSystem);
        riskLevel = classifyRisk(riskLevel, mutating, name);
        parameterSchema = parameterSchema == null ? ToolParameterSchema.empty() : parameterSchema;
        permissionPolicy = permissionPolicy == null ? ToolPermissionPolicy.allowAll() : permissionPolicy;
        auditPolicy = auditPolicy == null ? ToolAuditPolicy.standard() : auditPolicy;
        workloadType = workloadType == null ? AgentWorkloadType.OFFICE : workloadType;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public ToolDefinition(
            String id,
            String tenantId,
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
            ToolAuditPolicy auditPolicy,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                tenantId,
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
                permissionPolicy,
                auditPolicy,
                AgentWorkloadType.OFFICE,
                createdAt,
                updatedAt);
    }

    public ToolDefinition withEnabled(boolean nextEnabled) {
        return new ToolDefinition(
                id,
                tenantId,
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
                permissionPolicy,
                auditPolicy,
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
