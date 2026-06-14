package com.harnessagent.api;

import com.harnessagent.rag.KnowledgeCitation;
import java.util.List;

public record ChatResponse(
        String message,
        String runtimeUserId,
        String runtimeSessionId,
        boolean knowledgeBacked,
        String noAnswerReason,
        List<KnowledgeCitation> citations) {
}
