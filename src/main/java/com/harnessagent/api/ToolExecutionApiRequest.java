package com.harnessagent.api;

import java.util.Map;
import java.util.Set;

public record ToolExecutionApiRequest(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String toolId,
        Map<String, Object> parameters,
        Set<String> departments,
        Set<String> roles,
        boolean confirmed,
        String approvalId,
        String reviewerId,
        String idempotencyKey) {
}
