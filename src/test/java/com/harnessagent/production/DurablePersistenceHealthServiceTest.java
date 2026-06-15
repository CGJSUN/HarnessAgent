package com.harnessagent.production;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.rag.JdbcKnowledgeStore;
import com.harnessagent.security.JdbcSecurityAuditStore;
import com.harnessagent.security.SensitiveDataRedactor;
import com.harnessagent.session.JdbcSessionStore;
import com.harnessagent.tooling.JdbcToolStore;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class DurablePersistenceHealthServiceTest {

    @Test
    void passesWhenProductionJdbcStoresSchemaAndSnapshotProbeAreAvailable() {
        EmbeddedDatabase database = database();
        try {
            ProductionRuntimeProperties properties = productionJdbcProperties();
            JdbcTemplate jdbc = new JdbcTemplate(database);
            NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(database);
            ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
            DurablePersistenceHealthService service = new DurablePersistenceHealthService(
                    properties,
                    database,
                    new JdbcSessionStore(named),
                    new JdbcKnowledgeStore(jdbc, objectMapper),
                    new JdbcToolStore(jdbc, objectMapper),
                    new JdbcSecurityAuditStore(named, objectMapper),
                    new JdbcRuntimeTelemetry(jdbc, objectMapper, new SensitiveDataRedactor(), properties),
                    new JdbcBudgetCounterStore(named),
                    new JdbcAgentStateStore(named, new TenantStateKeyStrategy(), StateStorePlan.mysql("jdbc:h2:mem")),
                    new JdbcSnapshotStore(named));

            DurablePersistenceHealth health = service.check();

            assertThat(health.passed()).isTrue();
            assertThat(health.failureReasons()).isEmpty();
        } finally {
            database.shutdown();
        }
    }

    @Test
    void failsWhenProductionDurableStoresAreNotActive() {
        ProductionRuntimeProperties properties = productionJdbcProperties();
        DurablePersistenceHealthService service = new DurablePersistenceHealthService(
                properties,
                (DataSource) null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        DurablePersistenceHealth health = service.check();

        assertThat(health.passed()).isFalse();
        assertThat(health.failureReasons())
                .contains(
                        "Production session store must be JdbcSessionStore.",
                        "Durable persistence DataSource is not configured.");
    }

    @Test
    void failsWhenJdbcSnapshotStoreIsNotWritable() {
        EmbeddedDatabase database = database();
        try {
            ProductionRuntimeProperties properties = productionJdbcProperties();
            JdbcTemplate jdbc = new JdbcTemplate(database);
            NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(database);
            ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
            DurablePersistenceHealthService service = new DurablePersistenceHealthService(
                    properties,
                    database,
                    new JdbcSessionStore(named),
                    new JdbcKnowledgeStore(jdbc, objectMapper),
                    new JdbcToolStore(jdbc, objectMapper),
                    new JdbcSecurityAuditStore(named, objectMapper),
                    new JdbcRuntimeTelemetry(jdbc, objectMapper, new SensitiveDataRedactor(), properties),
                    new JdbcBudgetCounterStore(named),
                    new JdbcAgentStateStore(named, new TenantStateKeyStrategy(), StateStorePlan.mysql("jdbc:h2:mem")),
                    new FailingJdbcSnapshotStore(named));

            DurablePersistenceHealth health = service.check();

            assertThat(health.passed()).isFalse();
            assertThat(health.failureReasons())
                    .anyMatch(reason -> reason.contains("JDBC snapshot store is not writable/readable")
                            && reason.contains("not writable"));
        } finally {
            database.shutdown();
        }
    }

    private static EmbeddedDatabase database() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("classpath:db/migration/V1__durable_persistence.sql")
                .build();
    }

    private static ProductionRuntimeProperties productionJdbcProperties() {
        ProductionRuntimeProperties properties = new ProductionRuntimeProperties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        properties.getStateStore().setType(StateStoreType.MYSQL);
        properties.getStateStore().setMysqlDsn("jdbc:h2:mem:harness_agent");
        properties.getStateStore().setDurableImplementationWired(true);
        properties.getSchema().setName("harness_agent");
        properties.getSchema().setMigrationTool("flyway");
        properties.getSchema().setMigrationLocation("classpath:db/migration");
        properties.getDurableStores().setSession(StateStoreType.MYSQL);
        properties.getDurableStores().setKnowledge(StateStoreType.MYSQL);
        properties.getDurableStores().setTool(StateStoreType.MYSQL);
        properties.getDurableStores().setAudit(StateStoreType.MYSQL);
        properties.getDurableStores().setTelemetry(StateStoreType.MYSQL);
        properties.getDurableStores().setBudgetCounter(StateStoreType.MYSQL);
        properties.getTelemetry().setDurableStoreEnabled(true);
        properties.getSnapshotStore().setType(SnapshotStoreType.JDBC);
        properties.getSnapshotStore().setUri("jdbc://snapshot");
        properties.getSnapshotStore().setImplementationWired(true);
        return properties;
    }

    private static class FailingJdbcSnapshotStore extends JdbcSnapshotStore {

        FailingJdbcSnapshotStore(NamedParameterJdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public SnapshotMetadata save(SnapshotMetadata metadata, byte[] content) {
            throw new IllegalStateException("not writable");
        }

        @Override
        public boolean delete(String snapshotId) {
            return false;
        }
    }
}
