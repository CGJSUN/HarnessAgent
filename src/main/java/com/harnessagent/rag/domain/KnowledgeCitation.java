package com.harnessagent.rag.domain;

public record KnowledgeCitation(
        String sourceId,
        String title,
        String version,
        int chunkIndex,
        String chunkId,
        KnowledgeSourceType sourceType,
        String sourceUri) {

    public KnowledgeCitation(String sourceId, String title, String version, int chunkIndex, String chunkId) {
        this(sourceId, title, version, chunkIndex, chunkId, KnowledgeSourceType.INLINE_TEXT, "");
    }

    public KnowledgeCitation {
        sourceType = sourceType == null ? KnowledgeSourceType.INLINE_TEXT : sourceType;
        sourceUri = sourceUri == null ? "" : sourceUri;
    }
}
