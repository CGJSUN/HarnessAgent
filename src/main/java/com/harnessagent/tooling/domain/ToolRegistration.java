package com.harnessagent.tooling.domain;

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
        ToolAuditPolicy auditPolicy) {
}
