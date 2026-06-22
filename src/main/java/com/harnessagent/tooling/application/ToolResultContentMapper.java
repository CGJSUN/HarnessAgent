package com.harnessagent.tooling.application;

import com.harnessagent.chat.domain.ContentBlock;
import com.harnessagent.tooling.execution.ToolExecutionResult;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolResultContentMapper {

    private ToolResultContentMapper() {
    }

    public static ContentBlock toContentBlock(String toolName, ToolExecutionResult result) {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("executionId", result.executionId());
        structured.put("toolId", result.toolId());
        structured.put("status", result.status().name());
        structured.put("message", result.message());
        structured.put("output", result.output());
        if (!result.operationSummary().isEmpty()) {
            structured.put("operationSummary", result.operationSummary());
        }
        rawReference(result.output()).ifPresent(reference -> structured.put("rawReference", reference));
        return ContentBlock.toolResult(toolName, structured);
    }

    private static java.util.Optional<String> rawReference(Map<String, Object> output) {
        for (String key : java.util.List.of("rawReference", "rawRef", "uri")) {
            Object value = output.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return java.util.Optional.of(String.valueOf(value));
            }
        }
        return java.util.Optional.empty();
    }
}
