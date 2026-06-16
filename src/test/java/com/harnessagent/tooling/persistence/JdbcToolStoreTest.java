package com.harnessagent.tooling.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.persistence.JdbcStoreTestSupport;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import com.harnessagent.tooling.audit.ToolAuditRecord;
import com.harnessagent.tooling.domain.ToolAuditPolicy;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolExecutionStatus;
import com.harnessagent.tooling.domain.ToolParameterSchema;
import com.harnessagent.tooling.domain.ToolPermissionPolicy;
import com.harnessagent.tooling.domain.ToolRiskLevel;
import com.harnessagent.tooling.domain.ToolSourceType;
import com.harnessagent.tooling.execution.ToolExecutionResult;
import com.harnessagent.tooling.persistence.JdbcToolStore;

class JdbcToolStoreTest {

    @Test
    void persistsToolsAuditAndIdempotencyAcrossInstances() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            JdbcStoreTestSupport.createToolTables(jdbc);
            ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
            JdbcToolStore writer = new JdbcToolStore(jdbc, objectMapper);
            JdbcToolStore reader = new JdbcToolStore(jdbc, objectMapper);
            Instant now = Instant.parse("2026-06-15T00:00:00Z");
            ToolDefinition tool = tool(now);

            writer.saveTool(tool);
            writer.saveAudit(new ToolAuditRecord(
                    "audit-a",
                    now.plusSeconds(1),
                    "tenant-a",
                    "user-a",
                    "agent-a",
                    "session-a",
                    tool.id(),
                    tool.name(),
                    tool.sourceType(),
                    tool.riskLevel(),
                    ToolExecutionStatus.SUCCEEDED,
                    Map.of("ticketId", "T-1"),
                    Map.of("status", "approved"),
                    25,
                    "approval-a",
                    "reviewer-a",
                    "idem-a",
                    ""));
            ToolExecutionResult firstResult = ToolExecutionResult.success(tool.id(), Map.of("externalId", "E-1"));
            writer.saveIdempotentResult("tenant-a:tool-a:idem-a", "{ticketId=T-1}", firstResult);
            writer.saveIdempotentResult(
                    "tenant-a:tool-a:idem-a",
                    "{ticketId=T-2}",
                    ToolExecutionResult.success(tool.id(), Map.of("externalId", "E-2")));

            assertThat(reader.findTool("tool-a")).contains(tool);
            assertThat(reader.listTools("tenant-a")).containsExactly(tool);
            assertThat(reader.listTools("tenant-b")).isEmpty();
            assertThat(reader.listAudit("tenant-a"))
                    .extracting(ToolAuditRecord::id)
                    .containsExactly("audit-a");
            assertThat(reader.findIdempotentResult("tenant-a:tool-a:idem-a"))
                    .get()
                    .satisfies(record -> {
                        assertThat(record.parameterFingerprint()).isEqualTo("{ticketId=T-1}");
                        assertThat(record.result().output()).containsEntry("externalId", "E-1");
                    });

            ToolDefinition disabled = tool.withEnabled(false);
            writer.saveTool(disabled);

            assertThat(reader.findTool("tool-a")).contains(disabled);
        } finally {
            database.shutdown();
        }
    }

    private static ToolDefinition tool(Instant now) {
        return new ToolDefinition(
                "tool-a",
                "tenant-a",
                "ticket.update",
                "Update tickets",
                "ServiceDesk",
                "owner-a",
                ToolSourceType.INTERNAL,
                "service-desk",
                ToolRiskLevel.HIGH_RISK,
                true,
                true,
                new ToolParameterSchema(
                        Set.of("ticketId", "status"),
                        Set.of("comment"),
                        Map.of("status", Set.of("approved", "rejected")),
                        Set.of("token")),
                new ToolPermissionPolicy(Set.of("tenant-a"), Set.of("user-a"), Set.of("agent-a"), Set.of(), Set.of()),
                ToolAuditPolicy.enabled(Set.of("token"), Set.of("email")),
                now,
                now);
    }
}
