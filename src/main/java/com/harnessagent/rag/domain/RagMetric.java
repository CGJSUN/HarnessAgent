package com.harnessagent.rag.domain;

import java.time.Instant;

public record RagMetric(
        String ownerScopeId,
        String ownerId,
        String query,
        boolean hit,
        int candidateCount,
        int permittedCount,
        String failureReason,
        Instant createdAt) {
}
