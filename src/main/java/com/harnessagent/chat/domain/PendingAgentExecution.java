package com.harnessagent.chat.domain;

import java.time.Instant;
import java.util.UUID;

public record PendingAgentExecution(
        String runId,
        String mode,
        String status,
        String userMessageId,
        String runtimeUserId,
        String runtimeSessionId,
        Instant startedAt) {

    public PendingAgentExecution {
        runId = runId == null || runId.isBlank() ? UUID.randomUUID().toString() : runId.trim();
        mode = require(mode, "mode");
        status = status == null || status.isBlank() ? "PENDING" : status.trim();
        userMessageId = userMessageId == null ? "" : userMessageId.trim();
        runtimeUserId = require(runtimeUserId, "runtimeUserId");
        runtimeSessionId = require(runtimeSessionId, "runtimeSessionId");
        startedAt = startedAt == null ? Instant.now() : startedAt;
    }

    public static PendingAgentExecution pending(
            String mode,
            String userMessageId,
            String runtimeUserId,
            String runtimeSessionId) {
        return new PendingAgentExecution(
                UUID.randomUUID().toString(),
                mode,
                "PENDING",
                userMessageId,
                runtimeUserId,
                runtimeSessionId,
                Instant.now());
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
