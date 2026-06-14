package com.harnessagent.rag;

public record RetrievedKnowledge(
        KnowledgeCitation citation, String content, double score, double keywordScore, double vectorScore) {
}
