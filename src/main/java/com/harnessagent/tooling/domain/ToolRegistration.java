package com.harnessagent.tooling.domain;

import com.harnessagent.production.config.AgentWorkloadType;

public record ToolRegistration(
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
        AgentWorkloadType workloadType) {

    public ToolRegistration {
        outputSchema = outputSchema == null ? ToolOutputSchema.empty() : outputSchema;
    }

    public ToolRegistration(
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
            ToolActivityPolicy activityPolicy) {
        this(
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
                AgentWorkloadType.OFFICE);
    }

    public ToolRegistration(
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
            AgentWorkloadType workloadType) {
        this(
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
                workloadType);
    }

    public ToolRegistration(
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
            ToolActivityPolicy activityPolicy) {
        this(
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
                AgentWorkloadType.OFFICE);
    }
}
