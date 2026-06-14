package com.harnessagent.rag;

import java.time.Instant;

public record RagFeedback(
        String tenantId,
        String userId,
        String query,
        boolean helpful,
        String comment,
        Instant createdAt) {
}
