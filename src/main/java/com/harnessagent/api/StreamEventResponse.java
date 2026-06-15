package com.harnessagent.api;

import com.harnessagent.rag.KnowledgeCitation;
import java.util.List;
import java.util.Map;

public record StreamEventResponse(
        String type,
        String content,
        boolean terminal,
        String noAnswerReason,
        List<KnowledgeCitation> citations,
        Map<String, Object> metadata) {

    public StreamEventResponse(String type, String content, boolean terminal) {
        this(type, content, terminal, null, List.of(), Map.of());
    }

    public StreamEventResponse {
        citations = citations == null ? List.of() : List.copyOf(citations);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
