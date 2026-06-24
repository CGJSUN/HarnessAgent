package com.harnessagent.rag.application;

import com.harnessagent.rag.domain.MemoryRagProviderDescriptor;
import com.harnessagent.rag.domain.OwnerRetrievalPrincipal;
import com.harnessagent.rag.retrieval.KnowledgeRetrievalResult;

public interface MemoryRagProvider {

    MemoryRagProviderDescriptor descriptor();

    default String id() {
        return descriptor().id();
    }

    default void ingestDocument(KnowledgeDocumentInput input) {
        throw new IllegalStateException("Memory/RAG provider " + id() + " is not configured");
    }

    default KnowledgeRetrievalResult retrieve(OwnerRetrievalPrincipal principal, String query, int limit) {
        throw new IllegalStateException("Memory/RAG provider " + id() + " is not configured");
    }
}
