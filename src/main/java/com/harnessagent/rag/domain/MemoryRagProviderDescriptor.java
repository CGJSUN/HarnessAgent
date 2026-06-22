package com.harnessagent.rag.domain;

public record MemoryRagProviderDescriptor(
        String id,
        MemoryRagProviderType type,
        boolean configured,
        String description) {
}
