package com.harnessagent.api;

import com.harnessagent.tooling.ToolRiskLevel;
import com.harnessagent.tooling.ToolSourceType;
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
        Set<String> sensitiveResultFields,
        Set<String> allowedUsers,
        Set<String> allowedAgents,
        Set<String> allowedDepartments,
        Set<String> allowedRoles) {
}
