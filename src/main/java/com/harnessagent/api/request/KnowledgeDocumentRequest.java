package com.harnessagent.api.request;

import com.harnessagent.rag.domain.KnowledgeVisibility;
import com.harnessagent.rag.domain.KnowledgeSourceType;
import java.util.Set;

public record KnowledgeDocumentRequest(
        String ownerId,
        String agentId,
        String title,
        String version,
        KnowledgeVisibility visibility,
        Set<String> allowedOwnerIds,
        String updatePolicy,
        KnowledgeSourceType sourceType,
        String sourceUri,
        String content) {
}
