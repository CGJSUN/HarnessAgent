package com.harnessagent.production;

import com.harnessagent.rag.JdbcKnowledgeStore;
import com.harnessagent.rag.KnowledgeStore;
import com.harnessagent.security.JdbcSecurityAuditStore;
import com.harnessagent.security.SecurityAuditStore;
import com.harnessagent.session.JdbcSessionStore;
import com.harnessagent.session.SessionStore;
import com.harnessagent.tooling.JdbcToolStore;
import com.harnessagent.tooling.ToolStore;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DurablePersistenceHealthService {

    private static final List<String> REQUIRED_TABLES = List.of(
            "ha_session_messages",
            "ha_security_audit",
            "ha_budget_counters",
            "ha_agent_state",
            "ha_snapshot_metadata",
            "ha_snapshot_content",
            "ha_knowledge_sources",
            "ha_knowledge_chunks",
            "ha_rag_metrics",
            "ha_rag_feedback",
            "ha_tool_definitions",
            "ha_tool_audit_records",
            "ha_tool_idempotency_records",
            "ha_telemetry_events");

    private final ProductionRuntimeProperties properties;
    private final DataSource dataSource;
    private final SessionStore sessionStore;
    private final KnowledgeStore knowledgeStore;
    private final ToolStore toolStore;
    private final SecurityAuditStore securityAuditStore;
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
            ObjectProvider<SecurityAuditStore> securityAuditStore,
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
                securityAuditStore.getIfAvailable(),
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
            SecurityAuditStore securityAuditStore,
            RuntimeTelemetry runtimeTelemetry,
            BudgetCounterStore budgetCounterStore,
            AgentStateStore agentStateStore,
            SnapshotStore snapshotStore) {
        this.properties = properties;
        this.dataSource = dataSource;
        this.sessionStore = sessionStore;
        this.knowledgeStore = knowledgeStore;
        this.toolStore = toolStore;
        this.securityAuditStore = securityAuditStore;
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
        requireJdbcStore("session", properties.getDurableStores().getSession(), sessionStore, JdbcSessionStore.class, failures);
        requireJdbcStore("knowledge", properties.getDurableStores().getKnowledge(), knowledgeStore, JdbcKnowledgeStore.class, failures);
        requireJdbcStore("tool", properties.getDurableStores().getTool(), toolStore, JdbcToolStore.class, failures);
        requireJdbcStore("audit", properties.getDurableStores().getAudit(), securityAuditStore, JdbcSecurityAuditStore.class, failures);
        requireBudgetStore(failures);
        if (properties.getTelemetry().isDurableStoreEnabled()) {
            requireJdbcStore("telemetry", properties.getTelemetry().getDurableStoreType(),
                    runtimeTelemetry, JdbcRuntimeTelemetry.class, failures);
        }
        requireAgentStateStore(failures);
        requireSnapshotStore(failures);
        requireSchema(failures);
        checkSnapshotWritable(failures);
        return failures.isEmpty()
                ? DurablePersistenceHealth.healthy()
                : DurablePersistenceHealth.failed(failures);
    }

    private void requireAgentStateStore(List<String> failures) {
        if (properties.getStateStore().getType() == StateStoreType.MYSQL) {
            if (!(agentStateStore instanceof JdbcAgentStateStore)) {
                failures.add("AgentScope state store must be JdbcAgentStateStore for production MySQL state.");
            }
            return;
        }
        if (properties.getStateStore().getType() == StateStoreType.REDIS) {
            if (!(agentStateStore instanceof RedisAgentStateStore)) {
                failures.add("AgentScope state store must be RedisAgentStateStore for production Redis state.");
            }
        }
    }

    private void requireBudgetStore(List<String> failures) {
        StateStoreType type = properties.getDurableStores().getBudgetCounter();
        if (type == StateStoreType.MYSQL && !(budgetCounterStore instanceof JdbcBudgetCounterStore)) {
            failures.add("Production budget store must be JdbcBudgetCounterStore.");
            return;
        }
        if (type == StateStoreType.REDIS && !(budgetCounterStore instanceof RedisBudgetCounterStore)) {
            failures.add("Production budget store must be RedisBudgetCounterStore.");
            return;
        }
        if (type == StateStoreType.LOCAL_JSON) {
            failures.add("Production budget store must not use local-json.");
        }
    }

    private void requireSnapshotStore(List<String> failures) {
        SnapshotStoreType type = properties.getSnapshotStore().getType();
        if (type == SnapshotStoreType.JDBC && !(snapshotStore instanceof JdbcSnapshotStore)) {
            failures.add("JDBC snapshot store is configured but JdbcSnapshotStore is not active.");
        }
        if (type != SnapshotStoreType.NONE && type != SnapshotStoreType.JDBC) {
            failures.add(type + " snapshot store is configured but no production implementation is active.");
        }
    }

    private void requireSchema(List<String> failures) {
        if (dataSource == null) {
            failures.add("Durable persistence DataSource is not configured.");
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) {
                failures.add("Durable persistence DataSource is not valid.");
                return;
            }
        } catch (SQLException ex) {
            failures.add("Durable persistence DataSource is not reachable: " + ex.getMessage());
            return;
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String table : REQUIRED_TABLES) {
            try {
                jdbc.queryForObject("select count(*) from " + table + " where 1 = 0", Integer.class);
            } catch (RuntimeException ex) {
                failures.add("Missing durable persistence table: " + table);
            }
        }
    }

    private void checkSnapshotWritable(List<String> failures) {
        if (!properties.getSnapshotStore().isWritableCheckEnabled()
                || properties.getSnapshotStore().getType() != SnapshotStoreType.JDBC
                || !(snapshotStore instanceof JdbcSnapshotStore)) {
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
            snapshotStore.save(metadata, "ok".getBytes(StandardCharsets.UTF_8));
            Snapshot loaded = snapshotStore.load(snapshotId).orElseThrow(
                    () -> new IllegalStateException("snapshot health check load returned empty"));
            if (loaded.content().length == 0) {
                failures.add("JDBC snapshot store health check loaded empty content.");
            }
        } catch (RuntimeException ex) {
            failures.add("JDBC snapshot store is not writable/readable: " + ex.getMessage());
        } finally {
            snapshotStore.delete(snapshotId);
        }
    }

    private static void requireJdbcStore(
            String name,
            StateStoreType configuredType,
            Object store,
            Class<?> expectedType,
            List<String> failures) {
        if (configuredType == StateStoreType.LOCAL_JSON) {
            failures.add("Production " + name + " store must not use local-json.");
            return;
        }
        if (configuredType == StateStoreType.REDIS) {
            failures.add("Redis " + name + " store is not wired.");
            return;
        }
        if (!expectedType.isInstance(store)) {
            failures.add("Production " + name + " store must be " + expectedType.getSimpleName() + ".");
        }
    }
}
