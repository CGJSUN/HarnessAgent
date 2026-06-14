package com.harnessagent.rag;

import java.util.List;

public record KnowledgeRetrievalResult(
        boolean answered,
        String message,
        List<RetrievedKnowledge> results,
        List<KnowledgeCitation> citations) {

    public static KnowledgeRetrievalResult noAnswer(String message) {
        return new KnowledgeRetrievalResult(false, message, List.of(), List.of());
    }

    public static KnowledgeRetrievalResult answered(List<RetrievedKnowledge> results) {
        return new KnowledgeRetrievalResult(
                true,
                "已从可访问知识中找到相关内容。",
                results,
                results.stream().map(RetrievedKnowledge::citation).toList());
    }
}
