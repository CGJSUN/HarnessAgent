package com.harnessagent.api;

import java.util.Set;

public record ChatRequest(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String message,
        boolean knowledgeEnabled,
        Set<String> departments,
        Set<String> roles,
        int knowledgeLimit) {
}
