package com.harnessagent.session.domain;

import java.time.Instant;

public record SessionSummary(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        int messageCount,
        Instant lastMessageAt) {
}
