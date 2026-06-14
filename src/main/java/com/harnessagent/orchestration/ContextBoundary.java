package com.harnessagent.orchestration;

import java.util.Map;
import java.util.Set;

public record ContextBoundary(
        boolean shareMemory,
        boolean shareFiles,
        boolean shareToolOutputs,
        boolean shareRetrievedKnowledge,
        Set<String> allowedKeys) {

    public Map<String, Object> filter(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return Map.of();
        }
        if (allowedKeys == null || allowedKeys.isEmpty()) {
            return Map.of();
        }
        return context.entrySet().stream()
                .filter(entry -> allowedKeys.contains(entry.getKey()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
