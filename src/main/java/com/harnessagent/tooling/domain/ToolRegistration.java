package com.harnessagent.tooling.domain;

import com.harnessagent.production.config.AgentWorkloadType;

public record ToolRegistration(
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
        ToolOutputSchema outputSchema,
        ToolPermissionPolicy permissionPolicy,
        ToolAuditPolicy auditPolicy,
        AgentWorkloadType workloadType) {

    public ToolRegistration {
        outputSchema = outputSchema == null ? ToolOutputSchema.empty() : outputSchema;
    }

    public ToolRegistration(
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
            ToolAuditPolicy auditPolicy) {
        this(
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
                ToolOutputSchema.empty(),
                permissionPolicy,
                auditPolicy,
                AgentWorkloadType.OFFICE);
    }

    public ToolRegistration(
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
            AgentWorkloadType workloadType) {
        this(
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
                ToolOutputSchema.empty(),
                permissionPolicy,
                auditPolicy,
                workloadType);
    }

    public ToolRegistration(
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
            ToolOutputSchema outputSchema,
            ToolPermissionPolicy permissionPolicy,
            ToolAuditPolicy auditPolicy) {
        this(
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
                outputSchema,
                permissionPolicy,
                auditPolicy,
                AgentWorkloadType.OFFICE);
    }
}
