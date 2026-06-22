package com.harnessagent.rag.domain;

import java.time.Instant;

public record KnowledgeIndexMetadata(
        String sourceId,
        String agentId,
        KnowledgeSourceType sourceType,
        String sourceUri,
        String version,
        KnowledgeIndexStatus indexStatus,
        KnowledgeSourceStatus sourceStatus,
        Instant indexedAt) {
}
