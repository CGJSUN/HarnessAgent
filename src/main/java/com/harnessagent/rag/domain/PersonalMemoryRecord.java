package com.harnessagent.rag.domain;

import java.time.Instant;
import java.util.Optional;

public record PersonalMemoryRecord(
        String id,
        String tenantId,
        String ownerId,
        String agentId,
        String sessionId,
        MemoryLayer layer,
        String title,
        String content,
        MemoryWriteStatus status,
        Optional<String> sourceId,
        Instant createdAt,
        Instant updatedAt) {

    public PersonalMemoryRecord {
        layer = layer == null ? MemoryLayer.FACT_LEDGER : layer;
        status = status == null ? MemoryWriteStatus.PENDING_CONFIRMATION : status;
        sourceId = sourceId == null ? Optional.empty() : sourceId;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public PersonalMemoryRecord withStatus(MemoryWriteStatus status) {
        return new PersonalMemoryRecord(
                id,
                tenantId,
                ownerId,
                agentId,
                sessionId,
                layer,
                title,
                content,
                status,
                sourceId,
                createdAt,
                Instant.now());
    }

    public PersonalMemoryRecord withSource(String sourceId) {
        return new PersonalMemoryRecord(
                id,
                tenantId,
                ownerId,
                agentId,
                sessionId,
                layer,
                title,
                content,
                MemoryWriteStatus.CONFIRMED,
                Optional.of(sourceId),
                createdAt,
                Instant.now());
    }

    public PersonalMemoryRecord redactedDeleted() {
        return new PersonalMemoryRecord(
                id,
                tenantId,
                ownerId,
                agentId,
                "",
                layer,
                "deleted memory",
                "",
                MemoryWriteStatus.DELETED,
                Optional.empty(),
                createdAt,
                Instant.now());
    }
}
