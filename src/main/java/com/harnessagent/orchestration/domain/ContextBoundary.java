package com.harnessagent.orchestration.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        Map<String, Object> result = new LinkedHashMap<>();
        context.forEach((key, value) -> {
            if (key != null && allowedKeys.contains(key) && allowedByCategory(key)) {
                result.put(key, filterNestedValue(value));
            }
        });
        return Map.copyOf(result);
    }

    public long blockedCategoryCount(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return 0;
        }
        return countBlockedCategories(context);
    }

    private Object filterNestedValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> {
                if (nestedKey != null && allowedByCategory(String.valueOf(nestedKey))) {
                    nested.put(String.valueOf(nestedKey), filterNestedValue(nestedValue));
                }
            });
            return Map.copyOf(nested);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> filtered = new ArrayList<>();
            iterable.forEach(item -> filtered.add(filterNestedValue(item)));
            return List.copyOf(filtered);
        }
        return value;
    }

    private long countBlockedCategories(Object value) {
        if (value instanceof Map<?, ?> map) {
            long blocked = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && !allowedByCategory(String.valueOf(entry.getKey()))) {
                    blocked++;
                }
                blocked += countBlockedCategories(entry.getValue());
            }
            return blocked;
        }
        if (value instanceof Iterable<?> iterable) {
            long blocked = 0;
            for (Object item : iterable) {
                blocked += countBlockedCategories(item);
            }
            return blocked;
        }
        return 0;
    }

    public boolean allowedByCategory(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        if (!shareMemory && (normalized.contains("memory") || normalized.contains("memories"))) {
            return false;
        }
        if (!shareFiles && (normalized.contains("file") || normalized.contains("path"))) {
            return false;
        }
        if (!shareToolOutputs && (normalized.contains("toolresult") || normalized.contains("tooloutput"))) {
            return false;
        }
        if (!shareRetrievedKnowledge && (normalized.contains("citation") || normalized.contains("knowledge"))) {
            return false;
        }
        return true;
    }
}
