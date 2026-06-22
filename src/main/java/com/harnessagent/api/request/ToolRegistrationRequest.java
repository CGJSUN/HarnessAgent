package com.harnessagent.api.request;

import com.harnessagent.production.config.AgentWorkloadType;
import com.harnessagent.tooling.domain.ToolRiskLevel;
import com.harnessagent.tooling.domain.ToolSourceType;
import java.util.Map;
import java.util.Set;

public record ToolRegistrationRequest(
        String tenantId,
        String name,
        String description,
        String ownerSystem,
        String ownerId,
        ToolSourceType sourceType,
        String sourceRef,
        ToolRiskLevel riskLevel,
        Boolean mutating,
        Boolean enabled,
        Set<String> requiredParameters,
        Set<String> optionalParameters,
        Map<String, Set<String>> allowedValues,
        Set<String> sensitiveParameters,
        Set<String> workspacePathParameters,
        String outputType,
        Map<String, Object> outputSchema,
        Set<String> sensitiveResultFields,
        Set<String> allowedUsers,
        Set<String> allowedAgents,
        Set<String> allowedDepartments,
        Set<String> allowedRoles,
        AgentWorkloadType workloadType) {
}
