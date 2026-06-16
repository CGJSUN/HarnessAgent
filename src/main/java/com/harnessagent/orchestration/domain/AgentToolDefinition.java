package com.harnessagent.orchestration.domain;

import java.util.Set;

public record AgentToolDefinition(
        String toolName,
        String childAgentId,
        String inputContract,
        String outputContract,
        Set<String> requiredRoles) {
}
