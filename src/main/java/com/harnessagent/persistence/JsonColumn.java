package com.harnessagent.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonColumn {

    private JsonColumn() {
    }

    public static String write(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize JSON column", ex);
        }
    }

    public static <T> T read(ObjectMapper objectMapper, String value, TypeReference<T> type, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to deserialize JSON column", ex);
        }
    }

    public static <T> T read(ObjectMapper objectMapper, String value, Class<T> type) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("JSON column is empty");
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to deserialize JSON column", ex);
        }
    }
}
