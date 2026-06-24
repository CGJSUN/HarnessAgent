package com.harnessagent.console.view;

import com.harnessagent.rag.domain.KnowledgeSource;
import com.harnessagent.rag.domain.KnowledgeSourceStatus;
import com.harnessagent.rag.domain.KnowledgeSourceType;
import com.harnessagent.rag.domain.KnowledgeVisibility;
import java.time.Instant;
import java.util.Set;

public record KnowledgeSourceView(
        String id,
        String agentId,
        String title,
        String version,
        KnowledgeVisibility visibility,
        Set<String> allowedOwnerIds,
        KnowledgeSourceStatus status,
        KnowledgeSourceType sourceType,
        String sourceUri,
        String indexStatus,
        Instant indexedAt,
        String lastSyncResult,
        Instant updatedAt) {

    public static KnowledgeSourceView from(KnowledgeSource source) {
        return new KnowledgeSourceView(
                source.id(),
                source.agentId(),
                source.title(),
                source.version(),
                source.visibility(),
                source.allowedOwnerIds(),
                source.status(),
                source.sourceType(),
                source.sourceUri(),
                source.indexStatus().name(),
                source.indexedAt(),
                "ok",
                source.updatedAt());
    }
}
