package com.harnessagent.rag;

public record KnowledgeCitation(
        String sourceId, String title, String version, int chunkIndex, String chunkId) {
}
