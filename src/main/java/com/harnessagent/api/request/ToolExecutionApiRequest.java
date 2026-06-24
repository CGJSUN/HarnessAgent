package com.harnessagent.api.request;

import java.util.Map;

public record ToolExecutionApiRequest(
        String ownerId,
        String agentId,
        String sessionId,
        String toolId,
        Map<String, Object> parameters,
        boolean confirmed,
        String idempotencyKey) {
}
