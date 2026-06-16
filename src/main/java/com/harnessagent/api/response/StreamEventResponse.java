package com.harnessagent.api.response;

import com.harnessagent.rag.domain.KnowledgeCitation;
import java.util.List;
import java.util.Map;

public record StreamEventResponse(
        String type,
        String kind,
        String content,
        boolean terminal,
        String noAnswerReason,
        List<KnowledgeCitation> citations,
        Map<String, Object> metadata) {

    public StreamEventResponse(String type, String content, boolean terminal) {
        this(type, StreamEventKind.fromTypeName(type).name(), content, terminal, null, List.of(), Map.of());
    }

    public StreamEventResponse {
        kind = kind == null || kind.isBlank() ? StreamEventKind.fromTypeName(type).name() : kind;
        citations = citations == null ? List.of() : List.copyOf(citations);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
