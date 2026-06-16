package com.harnessagent.chat.domain;

import java.util.Map;

public record ContentBlock(
        ContentBlockType type,
        String text,
        String uri,
        String mimeType,
        String title,
        Map<String, Object> metadata) {

    public ContentBlock {
        if (type == null) {
            throw new IllegalArgumentException("content block type is required");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        switch (type) {
            case TEXT -> {
                if (text == null) {
                    throw new IllegalArgumentException("text content block requires text");
                }
            }
            case FILE, IMAGE, AUDIO, VIDEO -> {
                if (uri == null || uri.isBlank()) {
                    throw new IllegalArgumentException(type.name().toLowerCase() + " content block requires uri");
                }
            }
            case THINKING -> {
                if (text == null || text.isBlank()) {
                    throw new IllegalArgumentException("thinking content block requires text");
                }
            }
            case TOOL_RESULT -> {
                if (metadata.get("toolName") == null || String.valueOf(metadata.get("toolName")).isBlank()) {
                    throw new IllegalArgumentException("tool result content block requires toolName");
                }
                if (!metadata.containsKey("result")) {
                    throw new IllegalArgumentException("tool result content block requires result metadata");
                }
            }
        }
    }

    public static ContentBlock text(String text) {
        return new ContentBlock(ContentBlockType.TEXT, text, null, null, null, Map.of());
    }

    public static ContentBlock file(String uri, String mimeType, String title) {
        return reference(ContentBlockType.FILE, uri, mimeType, title);
    }

    public static ContentBlock image(String uri, String mimeType, String title) {
        return reference(ContentBlockType.IMAGE, uri, mimeType, title);
    }

    public static ContentBlock audio(String uri, String mimeType, String title) {
        return reference(ContentBlockType.AUDIO, uri, mimeType, title);
    }

    public static ContentBlock video(String uri, String mimeType, String title) {
        return reference(ContentBlockType.VIDEO, uri, mimeType, title);
    }

    public static ContentBlock thinking(String text) {
        return new ContentBlock(ContentBlockType.THINKING, text, null, null, null, Map.of());
    }

    public static ContentBlock toolResult(String toolName, Map<String, Object> result) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("tool result content block requires toolName");
        }
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("toolName", toolName);
        metadata.put("result", result == null ? Map.of() : Map.copyOf(result));
        return new ContentBlock(ContentBlockType.TOOL_RESULT, null, null, null, toolName, metadata);
    }

    private static ContentBlock reference(ContentBlockType type, String uri, String mimeType, String title) {
        return new ContentBlock(type, null, uri, mimeType, title, Map.of());
    }
}
