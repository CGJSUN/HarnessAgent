package com.harnessagent.chat;

import java.util.Set;

public record ChatCommand(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        String message,
        boolean knowledgeEnabled,
        Set<String> departments,
        Set<String> roles,
        int knowledgeLimit) {

    public ChatCommand(
            String tenantId, String userId, String agentId, String sessionId, String message) {
        this(tenantId, userId, agentId, sessionId, message, false, Set.of(), Set.of(), 5);
    }
}
