package com.harnessagent.api.request;

import com.harnessagent.rag.domain.MemoryLayer;

public record MemoryWriteRequest(
        String ownerId,
        String agentId,
        String sessionId,
        MemoryLayer layer,
        String title,
        String content,
        boolean requireConfirmation) {
}
