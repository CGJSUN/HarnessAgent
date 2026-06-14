package com.harnessagent.console;

import java.util.Map;

public record ToolConfirmationView(
        String toolId,
        String toolName,
        String sessionId,
        Map<String, Object> sanitizedInput) {
}
