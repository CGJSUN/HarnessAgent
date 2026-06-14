package com.harnessagent.production;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TelemetryEvent(
        String id,
        Instant occurredAt,
        TelemetryEventType type,
        String tenantId,
        String userId,
        String agentId,
        String component,
        long durationMillis,
        Map<String, Object> attributes) {

    public TelemetryEvent {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        type = type == null ? TelemetryEventType.API : type;
        tenantId = tenantId == null ? "" : tenantId.trim();
        userId = userId == null ? "" : userId.trim();
        agentId = agentId == null ? "" : agentId.trim();
        component = component == null ? "" : component.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
