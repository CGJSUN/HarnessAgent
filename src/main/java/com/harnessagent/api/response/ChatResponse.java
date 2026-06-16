package com.harnessagent.api.response;

import com.harnessagent.chat.domain.ChatExecutionSummary;
import com.harnessagent.chat.domain.ContentBlock;
import com.harnessagent.rag.domain.KnowledgeCitation;
import java.util.List;

public record ChatResponse(
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

    public ChatResponse {
        contentBlocks = contentBlocks == null ? List.of() : List.copyOf(contentBlocks);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
