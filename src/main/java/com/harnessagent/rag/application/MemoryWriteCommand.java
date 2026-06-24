package com.harnessagent.rag.application;

import com.harnessagent.rag.domain.MemoryLayer;

public record MemoryWriteCommand(
        String ownerScopeId,
        String ownerId,
        String agentId,
        String sessionId,
        MemoryLayer layer,
        String title,
        String content,
        boolean requireConfirmation) {
}
