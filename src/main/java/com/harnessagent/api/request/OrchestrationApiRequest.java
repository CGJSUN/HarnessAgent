package com.harnessagent.api.request;

import java.util.Map;
import java.util.Set;

public record OrchestrationApiRequest(
        String tenantId,
        String userId,
        Set<String> roles,
        Set<String> departments,
        String supervisorAgentId,
        String taskIntent,
        String task,
        Map<String, Object> context) {
}
