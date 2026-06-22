package com.harnessagent.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harnessagent.rag.domain.KnowledgeSourceRegistration;
import com.harnessagent.rag.domain.KnowledgeSourceType;
import com.harnessagent.rag.domain.KnowledgeVisibility;
import com.harnessagent.rag.domain.MemoryRagProviderDescriptor;
import com.harnessagent.rag.domain.MemoryRagProviderType;
import com.harnessagent.rag.domain.RetrievalPrincipal;
import com.harnessagent.rag.persistence.InMemoryKnowledgeStore;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemoryRagProviderRegistryTest {

    @Test
    void localProviderKeepsUnifiedRetrievalContractAndExternalProvidersAreExplicitPlaceholders() {
        InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
        KnowledgeService knowledgeService = new KnowledgeService(
                store,
                new TextChunker(),
                new TextTokenizer(),
                new KnowledgeRetrievalPolicy());
        MemoryRagProviderRegistry registry = MemoryRagProviderRegistry.withLocalProvider(
                new LocalMemoryRagProvider(knowledgeService));

        registry.provider("local").ingestDocument(new KnowledgeDocumentInput(
                new KnowledgeSourceRegistration(
                        "personal",
                        "owner-a",
                        "",
                        "本地知识",
                        "v1",
                        KnowledgeVisibility.RESTRICTED,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        "manual",
                        KnowledgeSourceType.LOCAL_FILE,
                        "workspace://knowledge/local.md"),
                "本地知识库保留统一引用来源契约。"));

        assertThat(registry.provider("local").retrieve(
                new RetrievalPrincipal("personal", "owner-a", Set.of(), Set.of()),
                "统一 引用 来源",
                3).citations()).singleElement()
                .satisfies(citation -> assertThat(citation.sourceUri()).isEqualTo("workspace://knowledge/local.md"));
        assertThat(registry.descriptors())
                .extracting(MemoryRagProviderDescriptor::type)
                .contains(
                        MemoryRagProviderType.LOCAL,
                        MemoryRagProviderType.BAILIAN,
                        MemoryRagProviderType.MEM0,
                        MemoryRagProviderType.REME,
                        MemoryRagProviderType.DIFY,
                        MemoryRagProviderType.HAYSTACK,
                        MemoryRagProviderType.RAGFLOW);
        assertThatThrownBy(() -> registry.provider("mem0").retrieve(
                new RetrievalPrincipal("personal", "owner-a", Set.of(), Set.of()),
                "anything",
                3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }
}
