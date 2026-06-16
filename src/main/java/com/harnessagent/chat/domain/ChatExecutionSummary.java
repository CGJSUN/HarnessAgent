package com.harnessagent.chat.domain;

public record ChatExecutionSummary(
        String status,
        boolean knowledgeBacked,
        int citationCount,
        String noAnswerReason,
        String runtimeUserId,
        String runtimeSessionId) {

    public ChatExecutionSummary {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("execution status is required");
        }
        if (citationCount < 0) {
            throw new IllegalArgumentException("citation count cannot be negative");
        }
    }
}
