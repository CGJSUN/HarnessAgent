package com.harnessagent.rag.domain;

public record RetrievedKnowledge(
        KnowledgeCitation citation, String content, double score, double keywordScore, double vectorScore) {
}
