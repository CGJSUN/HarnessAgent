package com.harnessagent.rag.application;

import com.harnessagent.rag.domain.MemoryLayer;

public record MemoryWriteCommand(
        String tenantId,
        String ownerId,
        String agentId,
        String sessionId,
        MemoryLayer layer,
        String title,
        String content,
        boolean requireConfirmation) {
}
