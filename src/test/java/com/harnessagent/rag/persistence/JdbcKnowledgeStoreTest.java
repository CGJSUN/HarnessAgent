package com.harnessagent.rag.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.persistence.JdbcStoreTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import com.harnessagent.rag.domain.KnowledgeChunk;
import com.harnessagent.rag.domain.KnowledgeIndexStatus;
import com.harnessagent.rag.domain.KnowledgeSource;
import com.harnessagent.rag.domain.KnowledgeSourceStatus;
import com.harnessagent.rag.domain.KnowledgeSourceType;
import com.harnessagent.rag.domain.KnowledgeVisibility;
import com.harnessagent.rag.domain.MemoryLayer;
import com.harnessagent.rag.domain.MemoryWriteStatus;
import com.harnessagent.rag.domain.PersonalMemoryRecord;
import com.harnessagent.rag.domain.RagFeedback;
import com.harnessagent.rag.domain.RagMetric;
import com.harnessagent.rag.persistence.JdbcKnowledgeStore;

class JdbcKnowledgeStoreTest {

    @Test
    void persistsSourcesChunksMetricsAndFeedbackAcrossInstances() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            JdbcStoreTestSupport.createKnowledgeTables(jdbc);
            ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
            JdbcKnowledgeStore writer = new JdbcKnowledgeStore(jdbc, objectMapper);
            JdbcKnowledgeStore reader = new JdbcKnowledgeStore(jdbc, objectMapper);
            Instant now = Instant.parse("2026-06-15T00:00:00Z");

            KnowledgeSource source = new KnowledgeSource(
                    "source-a",
                    "tenant-a",
                    "owner-a",
                    "agent-a",
                    "Handbook",
                    "v1",
                    KnowledgeVisibility.RESTRICTED,
                    Set.of("finance"),
                    Set.of("admin"),
                    Set.of("user-a"),
                    "manual",
                    KnowledgeSourceType.LOCAL_FILE,
                    "workspace://knowledge/handbook.md",
                    KnowledgeIndexStatus.INDEXED,
                    now.plusSeconds(5),
                    KnowledgeSourceStatus.ACTIVE,
                    now,
                    now.plusSeconds(10));
            writer.saveSource(source);
            writer.saveChunks(source.id(), List.of(
                    new KnowledgeChunk("chunk-a", source.id(), source.tenantId(), source.title(), source.version(),
                            0, "submit invoices within thirty days", Set.of("invoice", "thirty")),
                    new KnowledgeChunk("chunk-b", source.id(), source.tenantId(), source.title(), source.version(),
                            1, "manager approval is required", Set.of("manager", "approval"))));
            writer.recordMetric(new RagMetric("tenant-a", "user-a", "invoice", true, 2, 1, null, now));
            writer.recordFeedback(new RagFeedback("tenant-a", "user-a", "invoice", true, "useful", now));
            PersonalMemoryRecord memory = new PersonalMemoryRecord(
                    "memory-a",
                    "tenant-a",
                    "owner-a",
                    "agent-a",
                    "session-a",
                    MemoryLayer.FACT_LEDGER,
                    "Preference",
                    "Use Chinese.",
                    MemoryWriteStatus.CONFIRMED,
                    java.util.Optional.of(source.id()),
                    now,
                    now.plusSeconds(20));
            writer.saveMemory(memory);

            assertThat(reader.findSource("source-a")).contains(source);
            assertThat(reader.findSource("source-a")).map(KnowledgeSource::agentId).contains("agent-a");
            assertThat(reader.listSources("tenant-a")).containsExactly(source);
            assertThat(reader.listSources("tenant-b")).isEmpty();
            assertThat(reader.listChunks("tenant-a"))
                    .extracting(KnowledgeChunk::id)
                    .containsExactly("chunk-a", "chunk-b");
            assertThat(reader.listMetrics("tenant-a"))
                    .extracting(RagMetric::query)
                    .containsExactly("invoice");
            assertThat(reader.listFeedback("tenant-a"))
                    .extracting(RagFeedback::comment)
                    .containsExactly("useful");
            assertThat(reader.findMemory("memory-a")).contains(memory);
            assertThat(reader.listMemories("tenant-a", "owner-a", "agent-a")).containsExactly(memory);
            assertThat(reader.listMemories("tenant-a", "owner-a", "agent-b")).isEmpty();

            writer.saveSource(source.withStatus(KnowledgeSourceStatus.DELETED));
            writer.removeChunks(source.id());

            assertThat(reader.findSource("source-a"))
                    .map(KnowledgeSource::status)
                    .contains(KnowledgeSourceStatus.DELETED);
            assertThat(reader.listChunks("tenant-a")).isEmpty();

            KnowledgeSource revoked = source.withStatus(KnowledgeSourceStatus.REVOKED);
            writer.saveSource(revoked);

            assertThat(reader.findSource("source-a"))
                    .map(KnowledgeSource::status)
                    .contains(KnowledgeSourceStatus.REVOKED);
        } finally {
            database.shutdown();
        }
    }
}
