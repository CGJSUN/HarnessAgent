package com.harnessagent.api.request;

public record KnowledgeRetrievalRequest(
        String ownerId,
        String agentId,
        String query,
        int limit) {
}
