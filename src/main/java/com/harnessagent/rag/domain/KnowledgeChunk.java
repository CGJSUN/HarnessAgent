package com.harnessagent.rag.domain;

import java.util.Set;

public record KnowledgeChunk(
        String id,
        String sourceId,
        String tenantId,
        String title,
        String version,
        int chunkIndex,
        String content,
        Set<String> tokens,
        KnowledgeSourceType sourceType,
        String sourceUri) {

    public KnowledgeChunk(
            String id,
            String sourceId,
            String tenantId,
            String title,
            String version,
            int chunkIndex,
            String content,
            Set<String> tokens) {
        this(id, sourceId, tenantId, title, version, chunkIndex, content, tokens, KnowledgeSourceType.INLINE_TEXT, "");
    }

    public KnowledgeChunk {
        sourceType = sourceType == null ? KnowledgeSourceType.INLINE_TEXT : sourceType;
        sourceUri = sourceUri == null ? "" : sourceUri;
    }
}
