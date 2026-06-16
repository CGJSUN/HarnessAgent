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
        Set<String> tokens) {
}
