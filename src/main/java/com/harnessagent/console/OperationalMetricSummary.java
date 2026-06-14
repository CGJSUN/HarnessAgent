package com.harnessagent.console;

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
