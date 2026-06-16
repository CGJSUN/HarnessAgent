package com.harnessagent.rag.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import com.harnessagent.rag.application.KnowledgeDocumentInput;
import com.harnessagent.rag.application.KnowledgeRetrievalPolicy;
import com.harnessagent.rag.application.KnowledgeService;
import com.harnessagent.rag.application.TextChunker;
import com.harnessagent.rag.application.TextTokenizer;
import com.harnessagent.rag.domain.KnowledgeCitation;
import com.harnessagent.rag.domain.KnowledgeSource;
import com.harnessagent.rag.domain.KnowledgeSourceRegistration;
import com.harnessagent.rag.domain.KnowledgeSourceStatus;
import com.harnessagent.rag.domain.KnowledgeVisibility;
import com.harnessagent.rag.domain.RagFeedback;
import com.harnessagent.rag.domain.RagMetric;
import com.harnessagent.rag.domain.RetrievalPrincipal;
import com.harnessagent.rag.persistence.InMemoryKnowledgeStore;
import com.harnessagent.rag.retrieval.KnowledgeRetrievalResult;

class KnowledgeServiceTest {

    private final InMemoryKnowledgeStore store = new InMemoryKnowledgeStore();
    private final KnowledgeService service = new KnowledgeService(
            store,
            new TextChunker(),
            new TextTokenizer(),
            new KnowledgeRetrievalPolicy());

    @Test
    void ingestsDocumentAndReturnsCitations() {
        KnowledgeSource source = service.ingestDocument(new KnowledgeDocumentInput(
                registration("tenant-a", "owner-a", KnowledgeVisibility.PUBLIC),
                "报销制度要求发票在三十天内提交。"));

        KnowledgeRetrievalResult result = service.retrieve(
                principal("tenant-a", "user-a", Set.of(), Set.of()),
                "发票 三十天",
                3);

        assertThat(source.status()).isEqualTo(KnowledgeSourceStatus.ACTIVE);
        assertThat(result.answered()).isTrue();
        assertThat(result.results()).hasSize(1);
        KnowledgeCitation citation = result.citations().get(0);
        assertThat(citation.sourceId()).isEqualTo(source.id());
        assertThat(citation.title()).isEqualTo("员工制度");
        assertThat(citation.version()).isEqualTo("v1");
    }

    @Test
    void filtersRetrievalByPermission() {
        service.ingestDocument(new KnowledgeDocumentInput(
                registration("tenant-a", "owner-a", KnowledgeVisibility.RESTRICTED),
                "薪酬制度只有财务部门可见。"));

        KnowledgeRetrievalResult denied = service.retrieve(
                principal("tenant-a", "user-a", Set.of("engineering"), Set.of()),
                "薪酬制度",
                3);
        KnowledgeRetrievalResult allowed = service.retrieve(
                principal("tenant-a", "user-a", Set.of("finance"), Set.of()),
                "薪酬制度",
                3);

        assertThat(denied.answered()).isFalse();
        assertThat(allowed.answered()).isTrue();
    }

    @Test
    void invalidatesChunksWhenSourceIsDeleted() {
        KnowledgeSource source = service.ingestDocument(new KnowledgeDocumentInput(
                registration("tenant-a", "owner-a", KnowledgeVisibility.PUBLIC),
                "请假制度要求提前一天提交申请。"));

        service.deleteSource(source.id());

        KnowledgeRetrievalResult result = service.retrieve(
                principal("tenant-a", "user-a", Set.of(), Set.of()),
                "请假 提前",
                3);

        assertThat(result.answered()).isFalse();
        KnowledgeSource deleted = service.listSources("tenant-a").stream()
                .filter(item -> item.id().equals(source.id()))
                .findFirst()
                .orElseThrow();
        assertThat(deleted.status()).isEqualTo(KnowledgeSourceStatus.DELETED);
    }

    @Test
    void returnsNoAnswerAndRecordsMetricsWhenEvidenceIsMissing() {
        service.ingestDocument(new KnowledgeDocumentInput(
                registration("tenant-a", "owner-a", KnowledgeVisibility.PUBLIC),
                "办公用品采购需要直属主管审批。"));

        KnowledgeRetrievalResult result = service.retrieve(
                principal("tenant-a", "user-a", Set.of(), Set.of()),
                "完全无关的问题",
                3);

        assertThat(result.answered()).isFalse();
        assertThat(result.message()).contains("无法从当前可用知识中确定答案");
        java.util.List<RagMetric> metrics = service.listMetrics("tenant-a");
        RagMetric metric = metrics.get(metrics.size() - 1);
        assertThat(metric.hit()).isFalse();
        assertThat(metric.failureReason()).isEqualTo("no_permitted_evidence");
    }

    @Test
    void recordsUserFeedback() {
        service.recordFeedback("tenant-a", "user-a", "发票 三十天", true, "有帮助");

        assertThat(service.listFeedback("tenant-a"))
                .extracting(RagFeedback::comment)
                .containsExactly("有帮助");
    }

    private static KnowledgeSourceRegistration registration(
            String tenantId, String ownerId, KnowledgeVisibility visibility) {
        return new KnowledgeSourceRegistration(
                tenantId,
                ownerId,
                "员工制度",
                "v1",
                visibility,
                Set.of("finance"),
                Set.of(),
                Set.of(),
                "manual");
    }

    private static RetrievalPrincipal principal(
            String tenantId, String userId, Set<String> departments, Set<String> roles) {
        return new RetrievalPrincipal(tenantId, userId, departments, roles);
    }
}
