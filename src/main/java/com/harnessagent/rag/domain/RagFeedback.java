package com.harnessagent.rag.domain;

import java.time.Instant;

public record RagFeedback(
        String ownerScopeId,
        String ownerId,
        String query,
        boolean helpful,
        String comment,
        Instant createdAt) {
}
