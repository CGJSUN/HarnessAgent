package com.harnessagent.security;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SecurityAuditRecord(
        String id,
        Instant occurredAt,
        String tenantId,
        String userId,
        ResourceType resourceType,
        String resourceId,
        String action,
        Map<String, Object> sanitizedDetails) {

    public SecurityAuditRecord {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        tenantId = tenantId == null ? "" : tenantId.trim();
        userId = userId == null ? "" : userId.trim();
        resourceType = resourceType == null ? ResourceType.AUDIT : resourceType;
        resourceId = resourceId == null ? "" : resourceId.trim();
        action = action == null ? "" : action.trim();
        sanitizedDetails = sanitizedDetails == null ? Map.of() : Map.copyOf(sanitizedDetails);
    }
}
