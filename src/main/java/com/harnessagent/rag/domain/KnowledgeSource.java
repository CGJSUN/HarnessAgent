package com.harnessagent.rag.domain;

import java.time.Instant;
import java.util.Set;

public record KnowledgeSource(
        String id,
        String ownerScopeId,
        String ownerId,
        String agentId,
        String title,
        String version,
        KnowledgeVisibility visibility,
        Set<String> allowedOwnerIds,
        String updatePolicy,
        KnowledgeSourceType sourceType,
        String sourceUri,
        KnowledgeIndexStatus indexStatus,
        Instant indexedAt,
        KnowledgeSourceStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public KnowledgeSource {
        sourceType = sourceType == null ? KnowledgeSourceType.INLINE_TEXT : sourceType;
        sourceUri = sourceUri == null ? "" : sourceUri;
        agentId = agentId == null ? "" : agentId;
        allowedOwnerIds = allowedOwnerIds == null ? Set.of() : Set.copyOf(allowedOwnerIds);
        indexStatus = indexStatus == null ? KnowledgeIndexStatus.PENDING : indexStatus;
    }

    public KnowledgeSource withStatus(KnowledgeSourceStatus status) {
        return new KnowledgeSource(
                id,
                ownerScopeId,
                ownerId,
                agentId,
                title,
                version,
                visibility,
                allowedOwnerIds,
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
                ownerScopeId,
                ownerId,
                agentId,
                title,
                version,
                visibility,
                allowedOwnerIds,
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
