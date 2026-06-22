package com.harnessagent.rag.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.rag.domain.KnowledgeCitation;
import com.harnessagent.rag.domain.KnowledgeSourceRegistration;
import com.harnessagent.rag.domain.KnowledgeSourceType;
import com.harnessagent.rag.domain.KnowledgeVisibility;
import com.harnessagent.rag.domain.MemoryLayer;
import com.harnessagent.rag.domain.MemoryWriteStatus;
import com.harnessagent.rag.domain.PersonalDataExport;
import com.harnessagent.rag.domain.PersonalMemoryRecord;
import com.harnessagent.rag.domain.RetrievalPrincipal;
import com.harnessagent.rag.persistence.InMemoryKnowledgeStore;
import com.harnessagent.rag.retrieval.KnowledgeRetrievalResult;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PersonalMemoryServiceTest {

    private final InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
    private final KnowledgeService knowledgeService = new KnowledgeService(
            store,
            new TextChunker(),
            new TextTokenizer(),
            new KnowledgeRetrievalPolicy());
    private final PersonalMemoryService memoryService = new PersonalMemoryService(knowledgeService, store);

    @Test
    void confirmedFactLedgerMemoryBecomesRetrievableEvidenceAcrossSessions() {
        PersonalMemoryRecord pending = memoryService.requestWrite(new MemoryWriteCommand(
                "personal",
                "owner-a",
                "personal-assistant",
                "session-a",
                MemoryLayer.FACT_LEDGER,
                "长期语言偏好",
                "用户长期偏好是用中文回答。",
                true));

        assertThat(pending.status()).isEqualTo(MemoryWriteStatus.PENDING_CONFIRMATION);
        assertThat(pending.sourceId()).isEmpty();

        PersonalMemoryRecord confirmed = memoryService.confirmWrite(pending.id());
        KnowledgeRetrievalResult result = knowledgeService.retrieve(
                new RetrievalPrincipal("personal", "owner-a", "personal-assistant", Set.of(), Set.of()),
                "中文 回答 偏好",
                5);

        assertThat(confirmed.status()).isEqualTo(MemoryWriteStatus.CONFIRMED);
        assertThat(confirmed.sourceId()).isPresent();
        assertThat(result.answered()).isTrue();
        assertThat(result.citations()).singleElement()
                .satisfies(citation -> {
                    assertThat(citation.sourceType()).isEqualTo(KnowledgeSourceType.MEMORY);
                    assertThat(citation.sourceUri()).isEqualTo("memory://" + confirmed.id());
                });
    }

    @Test
    void memoryProjectionIsIsolatedByPersonalAgent() {
        PersonalMemoryRecord confirmed = memoryService.confirmWrite(memoryService.requestWrite(new MemoryWriteCommand(
                "personal",
                "owner-a",
                "agent-a",
                "session-a",
                MemoryLayer.FACT_LEDGER,
                "Agent A 偏好",
                "Agent A 记住用户喜欢番茄钟计划。",
                true)).id());

        KnowledgeRetrievalResult sameAgent = knowledgeService.retrieve(
                new RetrievalPrincipal("personal", "owner-a", "agent-a", Set.of(), Set.of()),
                "番茄钟 计划",
                5);
        KnowledgeRetrievalResult otherAgent = knowledgeService.retrieve(
                new RetrievalPrincipal("personal", "owner-a", "agent-b", Set.of(), Set.of()),
                "番茄钟 计划",
                5);

        assertThat(confirmed.sourceId()).isPresent();
        assertThat(sameAgent.answered()).isTrue();
        assertThat(otherAgent.answered()).isFalse();
    }

    @Test
    void rejectedAndDeletedMemoryDoesNotParticipateInRetrieval() {
        PersonalMemoryRecord rejected = memoryService.requestWrite(new MemoryWriteCommand(
                "personal",
                "owner-a",
                "personal-assistant",
                "session-a",
                MemoryLayer.SESSION_CONTEXT,
                "临时偏好",
                "本轮临时偏好是输出表格。",
                true));
        memoryService.rejectWrite(rejected.id());

        KnowledgeRetrievalResult afterReject = knowledgeService.retrieve(
                new RetrievalPrincipal("personal", "owner-a", "personal-assistant", Set.of(), Set.of()),
                "输出 表格",
                5);
        assertThat(afterReject.answered()).isFalse();

        PersonalMemoryRecord confirmed = memoryService.confirmWrite(memoryService.requestWrite(new MemoryWriteCommand(
                "personal",
                "owner-a",
                "personal-assistant",
                "session-b",
                MemoryLayer.AGENT_MEMORY_FILE,
                "代码风格",
                "用户喜欢最小可行改动。",
                true)).id());
        PersonalMemoryRecord deleted = memoryService.deleteMemory(confirmed.id());
        KnowledgeRetrievalResult afterDelete = knowledgeService.retrieve(
                new RetrievalPrincipal("personal", "owner-a", "personal-assistant", Set.of(), Set.of()),
                "最小 可行 改动",
                5);

        assertThat(deleted.status()).isEqualTo(MemoryWriteStatus.DELETED);
        assertThat(deleted.content()).isEmpty();
        assertThat(memoryService.listMemories("personal", "owner-a", "personal-assistant"))
                .extracting(PersonalMemoryRecord::id)
                .doesNotContain(confirmed.id());
        assertThat(memoryService.exportPersonalData("personal", "owner-a", "personal-assistant").memories())
                .extracting(PersonalMemoryRecord::id)
                .doesNotContain(confirmed.id());
        assertThat(afterDelete.answered()).isFalse();
    }

    @Test
    void exportsPersonalMemoryKnowledgeIndexMetadataAndCitationRecords() {
        PersonalMemoryRecord confirmed = memoryService.confirmWrite(memoryService.requestWrite(new MemoryWriteCommand(
                "personal",
                "owner-a",
                "personal-assistant",
                "session-a",
                MemoryLayer.FACT_LEDGER,
                "城市偏好",
                "用户计划六月去京都。",
                true)).id());
        knowledgeService.ingestDocument(new KnowledgeDocumentInput(
                new KnowledgeSourceRegistration(
                        "personal",
                        "owner-a",
                        "",
                        "行程文件",
                        "v2",
                        KnowledgeVisibility.RESTRICTED,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        "manual",
                        KnowledgeSourceType.LOCAL_FILE,
                        "workspace://artifacts/trip.md"),
                "京都第二天安排岚山。"));

        PersonalDataExport export = memoryService.exportPersonalData("personal", "owner-a", "personal-assistant");

        assertThat(export.memories()).extracting(PersonalMemoryRecord::id).contains(confirmed.id());
        assertThat(export.knowledgeSources())
                .anySatisfy(source -> assertThat(source.sourceUri()).isEqualTo("workspace://artifacts/trip.md"));
        assertThat(export.indexMetadata()).allSatisfy(metadata -> assertThat(metadata.indexStatus()).isNotNull());
        assertThat(export.citationRecords())
                .extracting(KnowledgeCitation::sourceType)
                .contains(KnowledgeSourceType.MEMORY, KnowledgeSourceType.LOCAL_FILE);
    }
}
