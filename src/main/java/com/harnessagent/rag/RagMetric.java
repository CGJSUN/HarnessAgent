package com.harnessagent.rag;

import java.time.Instant;

public record RagMetric(
        String tenantId,
        String userId,
        String query,
        boolean hit,
        int candidateCount,
        int permittedCount,
        String failureReason,
        Instant createdAt) {
}
