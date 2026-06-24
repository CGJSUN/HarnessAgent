package com.harnessagent.api.request;

public record ChatRequest(
        String ownerId,
        String agentId,
        String sessionId,
        String message,
        boolean knowledgeEnabled,
        int knowledgeLimit) {
}
