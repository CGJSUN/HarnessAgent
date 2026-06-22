package com.harnessagent.tooling.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolOutputSchema(String outputType, Map<String, Object> schema) {

    public ToolOutputSchema {
        outputType = outputType == null || outputType.isBlank() ? "application/json" : outputType.trim();
        schema = safeMap(schema);
    }

    public static ToolOutputSchema empty() {
        return new ToolOutputSchema("application/json", Map.of());
    }

    public static ToolOutputSchema structured(String outputType, Map<String, Object> schema) {
        return new ToolOutputSchema(outputType, schema);
    }

    private static Map<String, Object> safeMap(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                result.put(key.trim(), value);
            }
        });
        return Map.copyOf(result);
    }
}
