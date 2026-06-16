package com.harnessagent.console.view;

public record OperationalMetricSummary(
        long sessionCount,
        long modelOrAgentEvents,
        long toolCalls,
        long ragHits,
        long ragMisses,
        long failures,
        long totalDurationMillis,
        long feedbackCount) {
}
