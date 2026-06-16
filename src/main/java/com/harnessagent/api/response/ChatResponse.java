package com.harnessagent.api.response;

import com.harnessagent.rag.domain.KnowledgeCitation;
import java.util.List;

public record ChatResponse(
        String message,
        String runtimeUserId,
        String runtimeSessionId,
        boolean knowledgeBacked,
        String noAnswerReason,
        List<KnowledgeCitation> citations) {
}
