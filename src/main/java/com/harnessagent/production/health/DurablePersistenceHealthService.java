package com.harnessagent.production.health;

import com.harnessagent.persistence.DurableBackendType;
import com.harnessagent.persistence.DurableStoreCapability;
import com.harnessagent.rag.persistence.KnowledgeStore;
import com.harnessagent.security.persistence.SecurityActivityStore;
import com.harnessagent.session.persistence.SessionStore;
import com.harnessagent.tooling.persistence.ToolStore;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.harnessagent.production.budget.BudgetCounterStore;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.snapshot.Snapshot;
import com.harnessagent.production.snapshot.SnapshotMetadata;
import com.harnessagent.production.snapshot.SnapshotStore;
import com.harnessagent.production.snapshot.SnapshotStoreType;
import com.harnessagent.production.state.AgentStateStore;
import com.harnessagent.production.state.StateStoreType;
import com.harnessagent.production.telemetry.RuntimeTelemetry;

@Service
public class DurablePersistenceHealthService {

    private static final Logger log = LoggerFactory.getLogger(DurablePersistenceHealthService.class);

    private static final List<String> REQUIRED_TABLES = List.of(
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

    private static final Map<String, List<String>> REQUIRED_COLUMNS = Map.ofEntries(
            Map.entry("ha_session_messages",
                    List.of("owner_scope_id", "owner_id", "agent_id", "session_id")),
            Map.entry("ha_security_activity",
                    List.of("owner_scope_id", "owner_id", "resource_type", "resource_id")),
            Map.entry("ha_budget_counters",
                    List.of("owner_scope_id", "owner_id", "agent_id", "resource_id")),
            Map.entry("ha_agent_state",
                    List.of("owner_scope_id", "owner_id", "agent_id", "session_id", "scope")),
            Map.entry("ha_snapshot_metadata",
                    List.of("owner_scope_id", "owner_id", "agent_id", "session_id")),
            Map.entry("ha_knowledge_sources",
                    List.of(
                            "owner_scope_id",
                            "owner_id",
                            "agent_id",
                            "allowed_owners_json",
                            "source_type",
                            "source_uri",
                            "index_status",
                            "indexed_at")),
            Map.entry("ha_knowledge_chunks",
                    List.of("owner_scope_id", "owner_id", "agent_id", "source_type", "source_uri")),
            Map.entry("ha_personal_memories",
                    List.of(
                    "owner_scope_id",
                    "owner_id",
                    "agent_id",
                    "session_id",
                    "layer_name",
                    "title",
                    "content",
                    "status",
                    "source_id")),
            Map.entry("ha_rag_metrics",
                    List.of("owner_scope_id", "owner_id", "created_at")),
            Map.entry("ha_rag_feedback",
                    List.of("owner_scope_id", "owner_id", "created_at")),
            Map.entry("ha_tool_definitions",
                    List.of("owner_scope_id", "owner_id", "workload_type", "output_schema_json")),
            Map.entry("ha_tool_activity_records",
                    List.of("owner_scope_id", "owner_id", "agent_id", "session_id", "tool_id")),
            Map.entry("ha_tool_idempotency_records",
                    List.of("owner_scope_id", "owner_id", "agent_id", "session_id", "tool_id")),
            Map.entry("ha_tool_pending_confirmations",
                    List.of(
                    "confirmation_id",
                    "owner_scope_id",
                    "owner_id",
                    "agent_id",
                    "session_id",
                    "tool_id",
                    "status",
                    "parameters_json",
                    "sanitized_input_json",
                    "operation_summary_json",
                    "parameter_fingerprint")),
            Map.entry("ha_telemetry_events",
                    List.of("owner_scope_id", "owner_id", "agent_id", "occurred_at")));

    private final ProductionRuntimeProperties properties;
    private final DataSource dataSource;
    private final SessionStore sessionStore;
    private final KnowledgeStore knowledgeStore;
    private final ToolStore toolStore;
    private final SecurityActivityStore securityActivityStore;
    private final RuntimeTelemetry runtimeTelemetry;
    private final BudgetCounterStore budgetCounterStore;
    private final AgentStateStore agentStateStore;
    private final SnapshotStore snapshotStore;

    @Autowired
    public DurablePersistenceHealthService(
            ProductionRuntimeProperties properties,
            ObjectProvider<DataSource> dataSource,
            ObjectProvider<SessionStore> sessionStore,
            ObjectProvider<KnowledgeStore> knowledgeStore,
            ObjectProvider<ToolStore> toolStore,
            ObjectProvider<SecurityActivityStore> securityActivityStore,
            ObjectProvider<RuntimeTelemetry> runtimeTelemetry,
            ObjectProvider<BudgetCounterStore> budgetCounterStore,
            ObjectProvider<AgentStateStore> agentStateStore,
            ObjectProvider<SnapshotStore> snapshotStore) {
        this(
                properties,
                dataSource.getIfAvailable(),
                sessionStore.getIfAvailable(),
                knowledgeStore.getIfAvailable(),
                toolStore.getIfAvailable(),
                securityActivityStore.getIfAvailable(),
                runtimeTelemetry.getIfAvailable(),
                budgetCounterStore.getIfAvailable(),
                agentStateStore.getIfAvailable(),
                snapshotStore.getIfAvailable());
    }

    DurablePersistenceHealthService(
            ProductionRuntimeProperties properties,
            DataSource dataSource,
            SessionStore sessionStore,
            KnowledgeStore knowledgeStore,
            ToolStore toolStore,
            SecurityActivityStore securityActivityStore,
            RuntimeTelemetry runtimeTelemetry,
            BudgetCounterStore budgetCounterStore,
            AgentStateStore agentStateStore,
            SnapshotStore snapshotStore) {
        this.properties = properties;
        this.dataSource = dataSource;
        this.sessionStore = sessionStore;
        this.knowledgeStore = knowledgeStore;
        this.toolStore = toolStore;
        this.securityActivityStore = securityActivityStore;
        this.runtimeTelemetry = runtimeTelemetry;
        this.budgetCounterStore = budgetCounterStore;
        this.agentStateStore = agentStateStore;
        this.snapshotStore = snapshotStore;
    }

    public DurablePersistenceHealth check() {
        if (!properties.isProduction()) {
            return DurablePersistenceHealth.healthy();
        }
        List<String> failures = new ArrayList<>();
        try {
            new ProductionRuntimeValidator(properties).validate();
        } catch (IllegalStateException ex) {
            failures.add(ex.getMessage());
        }
        requireStoreCapability("session", properties.getDurableStores().getSession(), sessionStore, failures);
        requireStoreCapability("knowledge", properties.getDurableStores().getKnowledge(), knowledgeStore, failures);
        requireStoreCapability("tool", properties.getDurableStores().getTool(), toolStore, failures);
        requireStoreCapability("activity", properties.getDurableStores().getActivity(), securityActivityStore, failures);
        requireBudgetStore(failures);
        if (properties.getTelemetry().isDurableStoreEnabled()) {
            requireStoreCapability("telemetry", properties.getTelemetry().getDurableStoreType(), runtimeTelemetry, failures);
        }
        requireAgentStateStore(failures);
        requireSnapshotStore(failures);
        requireSchema(failures);
        checkSnapshotWritable(failures);
        if (!failures.isEmpty()) {
            log.warn("production readiness blocked failureCount={}", failures.size());
        }
        return failures.isEmpty()
                ? DurablePersistenceHealth.healthy()
                : DurablePersistenceHealth.failed(failures);
    }

    private void requireAgentStateStore(List<String> failures) {
        requireStoreCapability("AgentScope state", properties.getStateStore().getType(), agentStateStore, failures);
    }

    private void requireBudgetStore(List<String> failures) {
        requireStoreCapability("budget", properties.getDurableStores().getBudgetCounter(), budgetCounterStore, failures);
    }

    private void requireSnapshotStore(List<String> failures) {
        SnapshotStoreType type = properties.getSnapshotStore().getType();
        if (type == SnapshotStoreType.JDBC && !hasStoreType(snapshotStore, DurableBackendType.MYSQL)) {
            failures.add("JDBC snapshot store is configured but no JDBC snapshot store capability is active.");
        }
        if (type != SnapshotStoreType.NONE && type != SnapshotStoreType.JDBC) {
            failures.add(type + " snapshot store is configured but no production implementation is active.");
        }
    }

    private void requireSchema(List<String> failures) {
        if (dataSource == null) {
            log.warn("production durable health failed reason={}", "datasource_missing");
            failures.add("Durable persistence DataSource is not configured.");
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) {
                failures.add("Durable persistence DataSource is not valid.");
                return;
            }
        } catch (SQLException ex) {
            log.error("production durable health failed reason={} errorType={}", "datasource_unreachable", ex.getClass().getSimpleName());
            failures.add("Durable persistence DataSource is not reachable: " + ex.getMessage());
            return;
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String table : REQUIRED_TABLES) {
            try {
                jdbc.queryForObject("select count(*) from " + table + " where 1 = 0", Integer.class);
            } catch (RuntimeException ex) {
                log.warn("production durable health failed reason={} table={}", "missing_table", table);
                failures.add("Missing durable persistence table: " + table);
                continue;
            }
            List<String> columns = REQUIRED_COLUMNS.getOrDefault(table, List.of());
            if (columns.isEmpty()) {
                continue;
            }
            try {
                jdbc.query("select " + String.join(", ", columns) + " from " + table + " where 1 = 0",
                        resultSet -> null);
            } catch (RuntimeException ex) {
                log.warn("production durable health failed reason={} table={}", "missing_columns", table);
                failures.add("Missing durable persistence columns on table: " + table);
            }
        }
    }

    private void checkSnapshotWritable(List<String> failures) {
        if (!properties.getSnapshotStore().isWritableCheckEnabled()
                || properties.getSnapshotStore().getType() != SnapshotStoreType.JDBC
                || !hasStoreType(snapshotStore, DurableBackendType.MYSQL)) {
            return;
        }
        String snapshotId = "health-check-" + Instant.now().toEpochMilli();
        SnapshotMetadata metadata = new SnapshotMetadata(
                snapshotId,
                "health-check",
                "health-agent",
                "health-session",
                "health-task",
                Instant.now(),
                SnapshotStoreType.JDBC,
                "");
        try {
            // The writable probe stores opaque bytes only; it never inspects or logs snapshot content.
            snapshotStore.save(metadata, "ok".getBytes(StandardCharsets.UTF_8));
            Snapshot loaded = snapshotStore.load(snapshotId).orElseThrow(
                    () -> new IllegalStateException("snapshot health check load returned empty"));
            if (loaded.content().length == 0) {
                failures.add("JDBC snapshot store health check loaded empty content.");
            }
        } catch (RuntimeException ex) {
            log.error("production snapshot health failed reason={} errorType={}", "snapshot_probe_failed", ex.getClass().getSimpleName());
            failures.add("JDBC snapshot store is not writable/readable: " + ex.getMessage());
        } finally {
            snapshotStore.delete(snapshotId);
        }
    }

    private static void requireStoreCapability(
            String name, StateStoreType configuredType, Object store, List<String> failures) {
        if (configuredType == StateStoreType.LOCAL_JSON) {
            failures.add("Production " + name + " store must not use local-json.");
            return;
        }
        DurableBackendType expectedType = toBackendType(configuredType);
        if (!hasStoreType(store, expectedType)) {
            failures.add("Production " + name + " store must expose " + configuredType + " durable capability.");
        }
    }

    private static boolean hasStoreType(Object store, DurableBackendType expectedType) {
        return store instanceof DurableStoreCapability capability
                && capability.durableBackendType() == expectedType;
    }

    private static DurableBackendType toBackendType(StateStoreType stateStoreType) {
        return switch (stateStoreType) {
            case MYSQL -> DurableBackendType.MYSQL;
            case REDIS -> DurableBackendType.REDIS;
            case LOCAL_JSON -> throw new IllegalArgumentException("local-json is not a durable backend");
        };
    }
}
