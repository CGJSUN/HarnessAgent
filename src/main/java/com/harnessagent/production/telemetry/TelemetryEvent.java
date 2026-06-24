package com.harnessagent.production.telemetry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TelemetryEvent(
        String id,
        Instant occurredAt,
        TelemetryEventType type,
        String ownerScopeId,
        String ownerId,
        String agentId,
        String component,
        long durationMillis,
        Map<String, Object> attributes) {

    public TelemetryEvent {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        type = type == null ? TelemetryEventType.API : type;
        ownerScopeId = ownerScopeId == null ? "" : ownerScopeId.trim();
        ownerId = ownerId == null ? "" : ownerId.trim();
        agentId = agentId == null ? "" : agentId.trim();
        component = component == null ? "" : component.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public String ownerId() {
        return ownerId;
    }
}
