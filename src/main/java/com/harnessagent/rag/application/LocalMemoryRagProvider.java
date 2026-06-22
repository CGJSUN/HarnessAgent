package com.harnessagent.rag.application;

import com.harnessagent.rag.domain.MemoryRagProviderDescriptor;
import com.harnessagent.rag.domain.MemoryRagProviderType;
import com.harnessagent.rag.domain.RetrievalPrincipal;
import com.harnessagent.rag.retrieval.KnowledgeRetrievalResult;

public class LocalMemoryRagProvider implements MemoryRagProvider {

    private final KnowledgeService knowledgeService;

    public LocalMemoryRagProvider(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public MemoryRagProviderDescriptor descriptor() {
        return new MemoryRagProviderDescriptor(
                "local",
                MemoryRagProviderType.LOCAL,
                true,
                "Local lexical memory and RAG provider backed by KnowledgeService.");
    }

    @Override
    public void ingestDocument(KnowledgeDocumentInput input) {
        knowledgeService.ingestDocument(input);
    }

    @Override
    public KnowledgeRetrievalResult retrieve(RetrievalPrincipal principal, String query, int limit) {
        return knowledgeService.retrieve(principal, query, limit);
    }
}
