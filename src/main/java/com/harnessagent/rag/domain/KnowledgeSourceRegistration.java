package com.harnessagent.rag.domain;

import java.util.Set;

public record KnowledgeSourceRegistration(
        String ownerScopeId,
        String ownerId,
        String agentId,
        String title,
        String version,
        KnowledgeVisibility visibility,
        Set<String> allowedOwnerIds,
        String updatePolicy,
        KnowledgeSourceType sourceType,
        String sourceUri) {

    public KnowledgeSourceRegistration {
        agentId = agentId == null ? "" : agentId;
        allowedOwnerIds = allowedOwnerIds == null ? Set.of() : Set.copyOf(allowedOwnerIds);
    }
}
