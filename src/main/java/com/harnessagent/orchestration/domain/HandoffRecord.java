package com.harnessagent.orchestration.domain;

import java.time.Instant;
import java.util.Map;

public record HandoffRecord(
        Instant occurredAt,
        String fromAgentId,
        String toAgentId,
        String reason,
        Map<String, Object> sharedContext) {

    public HandoffRecord {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        sharedContext = sharedContext == null ? Map.of() : Map.copyOf(sharedContext);
    }
}
