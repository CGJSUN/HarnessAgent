package com.harnessagent.rag.domain;

import java.time.Instant;
import java.util.Set;

public record KnowledgeSource(
        String id,
        String tenantId,
        String ownerId,
        String agentId,
        String title,
        String version,
        KnowledgeVisibility visibility,
        Set<String> allowedDepartments,
        Set<String> allowedRoles,
        Set<String> allowedUsers,
        String updatePolicy,
        KnowledgeSourceType sourceType,
        String sourceUri,
        KnowledgeIndexStatus indexStatus,
        Instant indexedAt,
        KnowledgeSourceStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public KnowledgeSource(
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
        this(
                id,
                tenantId,
                ownerId,
                "",
                title,
                version,
                visibility,
                allowedDepartments,
                allowedRoles,
                allowedUsers,
                updatePolicy,
                KnowledgeSourceType.INLINE_TEXT,
                "",
                status == KnowledgeSourceStatus.DELETED ? KnowledgeIndexStatus.DELETED : KnowledgeIndexStatus.INDEXED,
                updatedAt,
                status,
                createdAt,
                updatedAt);
    }

    public KnowledgeSource {
        sourceType = sourceType == null ? KnowledgeSourceType.INLINE_TEXT : sourceType;
        sourceUri = sourceUri == null ? "" : sourceUri;
        agentId = agentId == null ? "" : agentId;
        indexStatus = indexStatus == null ? KnowledgeIndexStatus.PENDING : indexStatus;
    }

    public KnowledgeSource withStatus(KnowledgeSourceStatus status) {
        return new KnowledgeSource(
                id,
                tenantId,
                ownerId,
                agentId,
                title,
                version,
                visibility,
                allowedDepartments,
                allowedRoles,
                allowedUsers,
                updatePolicy,
                sourceType,
                sourceUri,
                status == KnowledgeSourceStatus.DELETED ? KnowledgeIndexStatus.DELETED : indexStatus,
                indexedAt,
                status,
                createdAt,
                Instant.now());
    }

    public KnowledgeSource withIndexStatus(KnowledgeIndexStatus indexStatus, Instant indexedAt) {
        return new KnowledgeSource(
                id,
                tenantId,
                ownerId,
                agentId,
                title,
                version,
                visibility,
                allowedDepartments,
                allowedRoles,
                allowedUsers,
                updatePolicy,
                sourceType,
                sourceUri,
                indexStatus,
                indexedAt,
                status,
                createdAt,
                Instant.now());
    }
}
