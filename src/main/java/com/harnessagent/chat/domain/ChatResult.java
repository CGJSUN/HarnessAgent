package com.harnessagent.chat.domain;

import com.harnessagent.rag.domain.KnowledgeCitation;
import java.util.List;

public record ChatResult(
        String message,
        String runtimeUserId,
        String runtimeSessionId,
        boolean knowledgeBacked,
        String noAnswerReason,
        List<KnowledgeCitation> citations) {

    public static ChatResult plain(String message, String runtimeUserId, String runtimeSessionId) {
        return new ChatResult(message, runtimeUserId, runtimeSessionId, false, null, List.of());
    }

    public static ChatResult knowledgeBacked(
            String message,
            String runtimeUserId,
            String runtimeSessionId,
            List<KnowledgeCitation> citations) {
        return new ChatResult(message, runtimeUserId, runtimeSessionId, true, null, citations);
    }

    public static ChatResult noAnswer(String message, String runtimeUserId, String runtimeSessionId) {
        return new ChatResult(message, runtimeUserId, runtimeSessionId, false, message, List.of());
    }
}
