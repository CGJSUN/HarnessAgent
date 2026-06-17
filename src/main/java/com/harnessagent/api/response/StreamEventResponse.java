package com.harnessagent.api.response;

import com.harnessagent.rag.domain.KnowledgeCitation;
import java.util.List;
import java.util.Map;

public record StreamEventResponse(
        String type,
        String kind,
        String channel,
        String content,
        boolean terminal,
        String noAnswerReason,
        List<KnowledgeCitation> citations,
        Map<String, Object> metadata) {

    public StreamEventResponse(String type, String content, boolean terminal) {
        this(type, StreamEventKind.fromTypeName(type).name(), defaultChannel(type), content, terminal, null, List.of(), Map.of());
    }

    public StreamEventResponse {
        kind = kind == null || kind.isBlank() ? StreamEventKind.fromTypeName(type).name() : kind;
        channel = channel == null || channel.isBlank() ? defaultChannel(type) : channel;
        citations = citations == null ? List.of() : List.copyOf(citations);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String defaultChannel(String type) {
        return switch (StreamEventKind.fromTypeName(type)) {
            case TEXT_DELTA, COMPLETION -> "USER_VISIBLE";
            case TOOL_EVENT -> "TOOL_EVENT";
            case ERROR, SUBAGENT_EVENT -> "DIAGNOSTIC";
            case MODEL_STATUS -> "SYSTEM_NOTICE";
        };
    }
}
