package com.harnessagent.session.domain;

import java.time.Instant;

public record SessionSummary(
        String ownerScopeId,
        String ownerId,
        String agentId,
        String sessionId,
        int messageCount,
        Instant lastMessageAt) {
}
