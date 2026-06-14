package com.harnessagent.console;

import com.harnessagent.tooling.ToolExecutionStatus;

public record ToolStatusView(
        String toolId,
        String toolName,
        ToolExecutionStatus status,
        String sessionId,
        long durationMillis) {
}
