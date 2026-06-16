package com.harnessagent.console.view;

import com.harnessagent.rag.domain.KnowledgeSource;
import com.harnessagent.rag.domain.KnowledgeSourceStatus;
import com.harnessagent.rag.domain.KnowledgeVisibility;
import java.time.Instant;
import java.util.Set;

public record KnowledgeSourceView(
        String id,
        String title,
        String version,
        KnowledgeVisibility visibility,
        Set<String> allowedDepartments,
        Set<String> allowedRoles,
        Set<String> allowedUsers,
        KnowledgeSourceStatus status,
        String indexStatus,
        String lastSyncResult,
        Instant updatedAt) {

    public static KnowledgeSourceView from(KnowledgeSource source) {
        return new KnowledgeSourceView(
                source.id(),
                source.title(),
                source.version(),
                source.visibility(),
                source.allowedDepartments(),
                source.allowedRoles(),
                source.allowedUsers(),
                source.status(),
                source.status().name().toLowerCase(),
                "ok",
                source.updatedAt());
    }
}
