package com.harnessagent.chat.domain;

import com.harnessagent.rag.domain.KnowledgeCitation;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.session.domain.ChatMessage;
import java.util.List;
import java.util.UUID;

public record ChatResult(
        String messageId,
        String sessionId,
        String message,
        List<ContentBlock> contentBlocks,
        ChatExecutionSummary executionSummary,
        String runtimeUserId,
        String runtimeSessionId,
        boolean knowledgeBacked,
        String noAnswerReason,
        List<KnowledgeCitation> citations) {

    public ChatResult {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId is required");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        contentBlocks = contentBlocks == null ? List.of() : List.copyOf(contentBlocks);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public static ChatResult plain(ChatMessage message, RuntimeContextScope context) {
        return fromMessage(message, context, "completed", false, null, List.of());
    }

    public static ChatResult knowledgeBacked(
            ChatMessage message,
            RuntimeContextScope context,
            List<KnowledgeCitation> citations) {
        return fromMessage(message, context, "completed", true, null, citations);
    }

    public static ChatResult noAnswer(ChatMessage message, RuntimeContextScope context) {
        return fromMessage(message, context, "knowledge_no_answer", false, message.content(), List.of());
    }

    public static ChatResult plain(String message, String runtimeUserId, String runtimeSessionId) {
        return legacy(message, runtimeUserId, runtimeSessionId, false, null, List.of(), "completed");
    }

    public static ChatResult knowledgeBacked(
            String message,
            String runtimeUserId,
            String runtimeSessionId,
            List<KnowledgeCitation> citations) {
        return legacy(message, runtimeUserId, runtimeSessionId, true, null, citations, "completed");
    }

    public static ChatResult noAnswer(String message, String runtimeUserId, String runtimeSessionId) {
        return legacy(message, runtimeUserId, runtimeSessionId, false, message, List.of(), "knowledge_no_answer");
    }

    private static ChatResult fromMessage(
            ChatMessage message,
            RuntimeContextScope context,
            String status,
            boolean knowledgeBacked,
            String noAnswerReason,
            List<KnowledgeCitation> citations) {
        return new ChatResult(
                message.id(),
                context.sessionId(),
                message.content(),
                message.contentBlocks(),
                new ChatExecutionSummary(
                        status,
                        knowledgeBacked,
                        citations == null ? 0 : citations.size(),
                        noAnswerReason,
                        context.runtimeUserId(),
                        context.runtimeSessionId()),
                context.runtimeUserId(),
                context.runtimeSessionId(),
                knowledgeBacked,
                noAnswerReason,
                citations);
    }

    private static ChatResult legacy(
            String message,
            String runtimeUserId,
            String runtimeSessionId,
            boolean knowledgeBacked,
            String noAnswerReason,
            List<KnowledgeCitation> citations,
            String status) {
        return new ChatResult(
                UUID.randomUUID().toString(),
                runtimeSessionId,
                message,
                List.of(ContentBlock.text(message)),
                new ChatExecutionSummary(
                        status,
                        knowledgeBacked,
                        citations == null ? 0 : citations.size(),
                        noAnswerReason,
                        runtimeUserId,
                        runtimeSessionId),
                runtimeUserId,
                runtimeSessionId,
                knowledgeBacked,
                noAnswerReason,
                citations);
    }
}
