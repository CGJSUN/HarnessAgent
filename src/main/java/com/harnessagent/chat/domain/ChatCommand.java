package com.harnessagent.chat.domain;

import com.harnessagent.runtime.PersonalRuntimeDefaults;
import java.util.Set;

public record ChatCommand(
        String ownerScopeId,
        String ownerId,
        String agentId,
        String sessionId,
        String message,
        boolean knowledgeEnabled,
        int knowledgeLimit) {

    public ChatCommand(
            String ownerScopeId, String ownerId, String agentId, String sessionId, String message) {
        this(ownerScopeId, ownerId, agentId, sessionId, message, false, 5);
    }

    public ChatCommand(
            String ownerScopeId,
            String ownerId,
            String agentId,
            String sessionId,
            String message,
            boolean knowledgeEnabled,
            Set<String> ignoredOwnerHints,
            Set<String> ignoredGroupHints,
            int knowledgeLimit) {
        this(ownerScopeId, ownerId, agentId, sessionId, message, knowledgeEnabled, knowledgeLimit);
    }

    public static ChatCommand forOwner(
            String ownerId,
            String agentId,
            String sessionId,
            String message,
            boolean knowledgeEnabled,
            int knowledgeLimit) {
        return new ChatCommand(
                PersonalRuntimeDefaults.PERSONAL_SCOPE_ID,
                ownerId,
                agentId,
                sessionId,
                message,
                knowledgeEnabled,
                knowledgeLimit);
    }
}
