package com.harnessagent.security.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataRedactor {

    private static final String REDACTED = "[REDACTED]";
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "token",
            "password",
            "secret",
            "apiKey",
            "api_key",
            "authorization",
            "credential");

    public String redactText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String redacted = EMAIL.matcher(text).replaceAll(REDACTED);
        return PHONE.matcher(redacted).replaceAll(REDACTED);
    }

    public Map<String, Object> redactMap(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> output = new LinkedHashMap<>();
        input.forEach((key, value) -> output.put(key, isSensitiveKey(key) ? REDACTED : redactValue(value)));
        return Map.copyOf(output);
    }

    private Object redactValue(Object value) {
        if (value instanceof String stringValue) {
            return redactText(stringValue);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                if (key != null) {
                    String stringKey = String.valueOf(key);
                    nested.put(stringKey, isSensitiveKey(stringKey) ? REDACTED : redactValue(nestedValue));
                }
            });
            return Map.copyOf(nested);
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.ArrayList<Object> items = new java.util.ArrayList<>();
            iterable.forEach(item -> items.add(redactValue(item)));
            return List.copyOf(items);
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        return SENSITIVE_KEYS.stream()
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(normalized::equals);
    }
}
