package com.harnessagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class DurablePersistenceMigrationTest {

    private static final List<String> TABLES = List.of(
            "ha_session_messages",
            "ha_security_activity",
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
            "ha_tool_activity_records",
            "ha_tool_idempotency_records",
            "ha_tool_pending_confirmations",
            "ha_telemetry_events",
            "ha_owner_scope_migration_activity");

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

            assertThat(tableComments).isEqualTo(17);
            assertThat(columnComments).isEqualTo(204);
            assertThat(sessionContentBlocksColumn).isTrue();
            assertThat(toolOutputSchemaColumn).isTrue();
            assertThat(toolPendingConfirmationsTable).isTrue();
        }
    }

    @Test
    void ownerScopeMigrationPreservesLegacyDurableRows() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration", "classpath:db/vendor-migration/h2")
                .target("10")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            seedLegacyRows(statement);
        }

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration", "classpath:db/vendor-migration/h2")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
                Statement statement = connection.createStatement()) {
            assertOwnerRow(statement, "ha_session_messages", "id = 'msg-a'");
            assertOwnerRow(statement, "ha_security_activity", "id = 'activity-a'");
            assertOwnerRow(statement, "ha_budget_counters", "counter_key = 'owner-scope:personal'");
            assertOwnerRow(statement, "ha_agent_state", "state_key = 'owner:owner-a:agent:agent-a:session:session-a:scope:memory'");
            assertOwnerRow(statement, "ha_snapshot_metadata", "id = 'snapshot-a'");
            assertOwnerRow(statement, "ha_knowledge_sources", "id = 'source-a'");
            assertOwnerRow(statement, "ha_knowledge_chunks", "id = 'chunk-a'");
            assertOwnerRow(statement, "ha_personal_memories", "id = 'memory-a'");
            assertOwnerRow(statement, "ha_rag_metrics", "query_text = 'query-a'");
            assertOwnerRow(statement, "ha_rag_feedback", "query_text = 'query-a'");
            assertOwnerRow(statement, "ha_tool_definitions", "id = 'tool-a'");
            assertOwnerRow(statement, "ha_tool_activity_records", "id = 'tool-activity-a'");
            assertOwnerRow(statement, "ha_tool_pending_confirmations", "confirmation_id = 'confirm-a'");
            assertOwnerRow(statement, "ha_telemetry_events", "id = 'telemetry-a'");

            try (ResultSet rs = statement.executeQuery("""
                    select owner_scope_id, owner_id, agent_id, session_id, tool_id
                    from ha_tool_idempotency_records
                    where idempotency_key = 'personal:owner-a:agent-a:session-a:tool-a:idem-a'
                    """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("owner_scope_id")).isEqualTo("personal");
                assertThat(rs.getString("owner_id")).isEqualTo("");
                assertThat(rs.getString("agent_id")).isEqualTo("");
                assertThat(rs.getString("session_id")).isEqualTo("");
                assertThat(rs.getString("tool_id")).isEqualTo("");
            }

            try (ResultSet rs = statement.executeQuery("""
                    select count(*) as records
                    from ha_owner_scope_migration_activity
                    where id = 'V11-owner-scope'
                    """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("records")).isEqualTo(1);
            }
        }
    }

    private static void seedLegacyRows(Statement statement) throws Exception {
        statement.executeUpdate("""
                insert into ha_session_messages (
                    id, tenant_id, user_id, agent_id, session_id, role, content, content_blocks_json, created_at
                ) values (
                    'msg-a', 'personal', 'owner-a', 'agent-a', 'session-a', 'USER', 'hello', '[]',
                    timestamp '2026-06-15 08:00:00'
                )
                """);
        statement.executeUpdate("""
                insert into ha_security_audit (
                    id, occurred_at, tenant_id, user_id, resource_type, resource_id, action, details_json
                ) values (
                    'activity-a', timestamp '2026-06-15 08:00:00', 'personal', 'owner-a',
                    'TOOL', 'tool-a', 'EXECUTE', '{}'
                )
                """);
        statement.executeUpdate("""
                insert into ha_budget_counters (
                    counter_key, tenant_id, user_id, agent_id, resource_id, requests, tokens, updated_at
                ) values (
                    'tenant:personal', 'personal', 'owner-a', 'agent-a', 'dashscope', 1, 10,
                    timestamp '2026-06-15 08:00:00'
                )
                """);
        statement.executeUpdate("""
                insert into ha_agent_state (
                    state_key, tenant_id, user_id, agent_id, session_id, scope, state_value, updated_at
                ) values (
                    'tenant:personal:user:owner-a:agent:agent-a:session:session-a:scope:memory',
                    'personal', 'owner-a', 'agent-a', 'session-a', 'memory', '{}',
                    timestamp '2026-06-15 08:00:00'
                )
                """);
        statement.executeUpdate("""
                insert into ha_snapshot_metadata (
                    id, tenant_id, agent_id, session_id, task_id, created_at, backend_type, location
                ) values (
                    'snapshot-a', 'personal', 'agent-a', 'session-a', 'task-a',
                    timestamp '2026-06-15 08:00:00', 'JDBC', 'jdbc://snapshot/snapshot-a'
                )
                """);
        statement.executeUpdate("""
                insert into ha_snapshot_content (snapshot_id, content)
                values ('snapshot-a', X'6F6B')
                """);
        statement.executeUpdate("""
                insert into ha_knowledge_sources (
                    id, tenant_id, owner_id, agent_id, title, version, visibility,
                    allowed_departments_json, allowed_roles_json, allowed_users_json,
                    update_policy, source_type, source_uri, index_status, indexed_at,
                    status, created_at, updated_at
                ) values (
                    'source-a', 'personal', 'owner-a', 'agent-a', 'title', 'v1', 'PRIVATE',
                    '[]', '[]', '[]', 'MANUAL', 'INLINE_TEXT', 'memory://source-a', 'INDEXED',
                    timestamp '2026-06-15 08:00:00', 'ACTIVE',
                    timestamp '2026-06-15 08:00:00', timestamp '2026-06-15 08:00:00'
                )
                """);
        statement.executeUpdate("""
                insert into ha_knowledge_chunks (
                    id, source_id, tenant_id, title, version, chunk_index, content, tokens_json, source_type, source_uri
                ) values (
                    'chunk-a', 'source-a', 'personal', 'title', 'v1', 0, 'chunk', '[]', 'INLINE_TEXT',
                    'memory://source-a'
                )
                """);
        statement.executeUpdate("""
                insert into ha_personal_memories (
                    id, tenant_id, owner_id, agent_id, session_id, layer_name, title, content,
                    status, source_id, created_at, updated_at
                ) values (
                    'memory-a', 'personal', 'owner-a', 'agent-a', 'session-a', 'FACT_LEDGER',
                    'memory', 'content', 'CONFIRMED', 'source-a',
                    timestamp '2026-06-15 08:00:00', timestamp '2026-06-15 08:00:00'
                )
                """);
        statement.executeUpdate("""
                insert into ha_rag_metrics (
                    tenant_id, user_id, query_text, hit, candidate_count, permitted_count, failure_reason, created_at
                ) values (
                    'personal', 'owner-a', 'query-a', true, 1, 1, null, timestamp '2026-06-15 08:00:00'
                )
                """);
        statement.executeUpdate("""
                insert into ha_rag_feedback (
                    tenant_id, user_id, query_text, helpful, comment_text, created_at
                ) values (
                    'personal', 'owner-a', 'query-a', true, 'ok', timestamp '2026-06-15 08:00:00'
                )
                """);
        statement.executeUpdate("""
                insert into ha_tool_definitions (
                    id, tenant_id, name, description, owner_system, owner_id, source_type, source_ref,
                    risk_level, mutating, enabled, parameter_schema_json, output_schema_json,
                    permission_policy_json, audit_policy_json, workload_type, created_at, updated_at
                ) values (
                    'tool-a', 'personal', 'tool', 'desc', 'owner', 'owner-a', 'INTERNAL', 'ref',
                    'READ_ONLY', false, true, '{}', '{}', '{}', '{}', 'OFFICE',
                    timestamp '2026-06-15 08:00:00', timestamp '2026-06-15 08:00:00'
                )
                """);
        statement.executeUpdate("""
                insert into ha_tool_audit_records (
                    id, occurred_at, tenant_id, user_id, agent_id, session_id, tool_id, tool_name, source_type,
                    risk_level, status, sanitized_input_json, sanitized_output_json, duration_millis,
                    approval_id, reviewer_id, idempotency_key, failure_reason
                ) values (
                    'tool-activity-a', timestamp '2026-06-15 08:00:00', 'personal', 'owner-a',
                    'agent-a', 'session-a', 'tool-a', 'tool', 'INTERNAL', 'READ_ONLY', 'SUCCEEDED',
                    '{}', '{}', 1, '', '', 'idem-a', ''
                )
                """);
        statement.executeUpdate("""
                insert into ha_tool_idempotency_records (
                    idempotency_key, parameter_fingerprint, result_json, created_at
                ) values (
                    'personal:owner-a:agent-a:session-a:tool-a:idem-a', 'fingerprint', '{}',
                    timestamp '2026-06-15 08:00:00'
                )
                """);
        statement.executeUpdate("""
                insert into ha_tool_pending_confirmations (
                    confirmation_id, tenant_id, user_id, agent_id, session_id, tool_id, tool_name,
                    source_type, risk_level, status, parameters_json, sanitized_input_json,
                    operation_summary_json, parameter_fingerprint, idempotency_key, created_at,
                    updated_at, expires_at, decided_at, decision_reason
                ) values (
                    'confirm-a', 'personal', 'owner-a', 'agent-a', 'session-a', 'tool-a', 'tool',
                    'INTERNAL', 'HIGH_RISK', 'PENDING', '{}', '{}', '{}', 'fingerprint', 'idem-a',
                    timestamp '2026-06-15 08:00:00', timestamp '2026-06-15 08:00:00',
                    timestamp '2026-06-16 08:00:00', null, ''
                )
                """);
        statement.executeUpdate("""
                insert into ha_telemetry_events (
                    id, occurred_at, type, tenant_id, user_id, agent_id, component, duration_millis, attributes_json
                ) values (
                    'telemetry-a', timestamp '2026-06-15 08:00:00', 'API', 'personal', 'owner-a',
                    'agent-a', 'api', 1, '{}'
                )
                """);
    }

    private static void assertOwnerRow(Statement statement, String table, String whereClause) throws Exception {
        try (ResultSet rs = statement.executeQuery(
                "select owner_scope_id, owner_id from " + table + " where " + whereClause)) {
            assertThat(rs.next()).as("owner row exists in %s", table).isTrue();
            assertThat(rs.getString("owner_scope_id")).as("audit in %s", table).isEqualTo("personal");
            assertThat(rs.getString("owner_id")).as("owner id in %s", table).isEqualTo("owner-a");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
