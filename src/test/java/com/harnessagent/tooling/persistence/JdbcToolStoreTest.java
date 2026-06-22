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
import com.harnessagent.tooling.domain.ToolOutputSchema;
import com.harnessagent.tooling.domain.ToolParameterSchema;
import com.harnessagent.tooling.domain.ToolPendingConfirmation;
import com.harnessagent.tooling.domain.ToolPendingConfirmationStatus;
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
            ToolPendingConfirmation pending = ToolPendingConfirmation.pending(
                    "tenant-a",
                    "user-a",
                    "agent-a",
                    "session-a",
                    tool,
                    Map.of("ticketId", "T-1", "status", "approved"),
                    Map.of("ticketId", "T-1", "status", "approved"),
                    Map.of("toolName", tool.name(), "riskLevel", tool.riskLevel().name()),
                    "{status=approved,ticketId=T-1}",
                    "idem-a");
            writer.savePendingConfirmation(pending);

            assertThat(reader.findTool("tool-a")).contains(tool);
            assertThat(reader.findTool("tool-a")).get()
                    .extracting(found -> found.outputSchema().outputType())
                    .isEqualTo("application/json");
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
            assertThat(reader.findPendingConfirmation(pending.confirmationId()))
                    .get()
                    .satisfies(found -> {
                        assertThat(found.status()).isEqualTo(ToolPendingConfirmationStatus.PENDING);
                        assertThat(found.parameters()).containsEntry("status", "approved");
                        assertThat(found.operationSummary()).containsEntry("toolName", tool.name());
                    });
            assertThat(reader.listPendingConfirmations("tenant-a", "user-a", "agent-a", "session-a"))
                    .extracting(ToolPendingConfirmation::confirmationId)
                    .containsExactly(pending.confirmationId());

            assertThat(writer.claimPendingConfirmation(pending.confirmationId(), "confirmed")).isTrue();
            assertThat(writer.claimPendingConfirmation(pending.confirmationId(), "confirmed again")).isFalse();
            assertThat(reader.findPendingConfirmation(pending.confirmationId()))
                    .get()
                    .satisfies(found -> {
                        assertThat(found.status()).isEqualTo(ToolPendingConfirmationStatus.CONFIRMED);
                        assertThat(found.decisionReason()).isEqualTo("confirmed");
                    });
            assertThat(reader.listPendingConfirmations("tenant-a", "user-a", "agent-a", "session-a")).isEmpty();

            ToolPendingConfirmation rejectedPending = ToolPendingConfirmation.pending(
                    "tenant-a",
                    "user-a",
                    "agent-a",
                    "session-a",
                    tool,
                    Map.of("ticketId", "T-2", "status", "rejected"),
                    Map.of("ticketId", "T-2", "status", "rejected"),
                    Map.of("toolName", tool.name(), "riskLevel", tool.riskLevel().name()),
                    "{status=rejected,ticketId=T-2}",
                    "idem-b");
            writer.savePendingConfirmation(rejectedPending);
            writer.savePendingConfirmation(rejectedPending.rejected("user rejected"));
            assertThat(reader.findPendingConfirmation(rejectedPending.confirmationId()))
                    .get()
                    .satisfies(found -> {
                        assertThat(found.status()).isEqualTo(ToolPendingConfirmationStatus.REJECTED);
                        assertThat(found.decisionReason()).isEqualTo("user rejected");
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
                ToolOutputSchema.structured("application/json", Map.of("type", "object")),
                now,
                now);
    }
}
