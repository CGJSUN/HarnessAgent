package com.harnessagent.rag.domain;

import java.time.Instant;
import java.util.Set;

public record KnowledgeSource(
        String id,
        String tenantId,
        String ownerId,
        String title,
        String version,
        KnowledgeVisibility visibility,
        Set<String> allowedDepartments,
        Set<String> allowedRoles,
        Set<String> allowedUsers,
        String updatePolicy,
        KnowledgeSourceStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public KnowledgeSource withStatus(KnowledgeSourceStatus status) {
        return new KnowledgeSource(
                id,
                tenantId,
                ownerId,
                title,
                version,
                visibility,
                allowedDepartments,
                allowedRoles,
                allowedUsers,
                updatePolicy,
                status,
                createdAt,
                Instant.now());
    }
}
