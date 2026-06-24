package com.harnessagent.security.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.harnessagent.security.domain.ResourceType;

public record SecurityActivityRecord(
        String id,
        Instant occurredAt,
        String ownerScopeId,
        String ownerId,
        ResourceType resourceType,
        String resourceId,
        String action,
        Map<String, Object> sanitizedDetails) {

    public SecurityActivityRecord {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        ownerScopeId = ownerScopeId == null ? "" : ownerScopeId.trim();
        ownerId = ownerId == null ? "" : ownerId.trim();
        resourceType = resourceType == null ? ResourceType.ACTIVITY : resourceType;
        resourceId = resourceId == null ? "" : resourceId.trim();
        action = action == null ? "" : action.trim();
        sanitizedDetails = sanitizedDetails == null ? Map.of() : Map.copyOf(sanitizedDetails);
    }
}
