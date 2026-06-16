package com.harnessagent.rag.domain;

public record KnowledgeCitation(
        String sourceId, String title, String version, int chunkIndex, String chunkId) {
}
