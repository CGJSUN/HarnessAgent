package com.harnessagent.console.view;

import com.harnessagent.tooling.domain.ToolExecutionStatus;

public record ToolStatusView(
        String toolId,
        String toolName,
        ToolExecutionStatus status,
        String sessionId,
        long durationMillis) {
}
