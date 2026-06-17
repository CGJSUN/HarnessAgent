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
        ToolPermissionPolicy permissionPolicy,
        ToolAuditPolicy auditPolicy,
        AgentWorkloadType workloadType) {

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
                permissionPolicy,
                auditPolicy,
                AgentWorkloadType.OFFICE);
    }
}
