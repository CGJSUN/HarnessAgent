package com.harnessagent.runtime;

public record RuntimeContextScope(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String runtimeUserId,
        String runtimeSessionId) {
}
