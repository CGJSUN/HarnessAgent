package com.harnessagent.rag;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP = 80;

    public List<String> chunk(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int cursor = 0;
        while (cursor < normalized.length()) {
            int end = Math.min(normalized.length(), cursor + DEFAULT_CHUNK_SIZE);
            chunks.add(normalized.substring(cursor, end));
            if (end == normalized.length()) {
                break;
            }
            cursor = Math.max(end - DEFAULT_OVERLAP, cursor + 1);
        }
        return chunks;
    }
}
