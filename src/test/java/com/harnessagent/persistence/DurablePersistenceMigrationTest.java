package com.harnessagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class DurablePersistenceMigrationTest {

    private static final List<String> TABLES = List.of(
            "ha_session_messages",
            "ha_security_audit",
            "ha_budget_counters",
            "ha_agent_state",
            "ha_snapshot_metadata",
            "ha_snapshot_content",
            "ha_knowledge_sources",
            "ha_knowledge_chunks",
            "ha_personal_memories",
            "ha_rag_metrics",
            "ha_rag_feedback",
            "ha_tool_definitions",
            "ha_tool_audit_records",
            "ha_tool_idempotency_records",
            "ha_tool_pending_confirmations",
            "ha_telemetry_events");

    @Test
    void h2VendorMigrationsApplyTableAndColumnComments() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration", "classpath:db/vendor-migration/h2")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            DatabaseMetaData metadata = connection.getMetaData();

            int tableComments = 0;
            int columnComments = 0;
            boolean sessionContentBlocksColumn = false;
            boolean toolOutputSchemaColumn = false;
            boolean toolPendingConfirmationsTable = false;
            for (String table : TABLES) {
                try (ResultSet tables = metadata.getTables(null, "PUBLIC", table, new String[] {"TABLE"})) {
                    assertThat(tables.next())
                            .as("table exists: %s", table)
                            .isTrue();
                    if ("ha_tool_pending_confirmations".equals(table)) {
                        toolPendingConfirmationsTable = true;
                    }
                    if (hasText(tables.getString("REMARKS"))) {
                        tableComments++;
                    }
                }
                try (ResultSet columns = metadata.getColumns(null, "PUBLIC", table, null)) {
                    while (columns.next()) {
                        if ("ha_session_messages".equals(table)
                                && "content_blocks_json".equals(columns.getString("COLUMN_NAME"))) {
                            sessionContentBlocksColumn = true;
                            assertThat(columns.getString("REMARKS")).contains("Structured content blocks");
                        }
                        if ("ha_tool_definitions".equals(table)
                                && "output_schema_json".equals(columns.getString("COLUMN_NAME"))) {
                            toolOutputSchemaColumn = true;
                            assertThat(columns.getString("REMARKS")).contains("output schema");
                        }
                        if (hasText(columns.getString("REMARKS"))) {
                            columnComments++;
                        }
                    }
                }
            }

            assertThat(tableComments).isEqualTo(16);
            assertThat(columnComments).isEqualTo(168);
            assertThat(sessionContentBlocksColumn).isTrue();
            assertThat(toolOutputSchemaColumn).isTrue();
            assertThat(toolPendingConfirmationsTable).isTrue();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
