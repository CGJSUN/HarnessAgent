package com.harnessagent.production.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.persistence.JdbcStoreTestSupport;
import com.harnessagent.security.application.SensitiveDataRedactor;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.infrastructure.JdbcRuntimeTelemetry;
import com.harnessagent.production.telemetry.TelemetryEvent;
import com.harnessagent.production.telemetry.TelemetryEventType;

class JdbcRuntimeTelemetryTest {

    @Test
    void recordsRedactedTelemetryAcrossInstancesWhenEnabled() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            JdbcStoreTestSupport.createTelemetryTables(jdbc);
            ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
            ProductionRuntimeProperties properties = new ProductionRuntimeProperties();
            properties.getObservability().setEnabled(true);
            JdbcRuntimeTelemetry writer = new JdbcRuntimeTelemetry(
                    jdbc, objectMapper, new SensitiveDataRedactor(), properties);
            JdbcRuntimeTelemetry reader = new JdbcRuntimeTelemetry(
                    jdbc, objectMapper, new SensitiveDataRedactor(), properties);

            TelemetryEvent event = writer.record(
                    TelemetryEventType.TOOL,
                    "owner-scope-a",
                    "user-a",
                    "agent-a",
                    "tool-service",
                    Duration.ofMillis(42),
                    Map.of(
                            "token", "secret",
                            "email", "owner@example.com",
                            "status", "SUCCEEDED"));

            assertThat(reader.list("owner-scope-a"))
                    .singleElement()
                    .satisfies(stored -> {
                        assertThat(stored.id()).isEqualTo(event.id());
                        assertThat(stored.type()).isEqualTo(TelemetryEventType.TOOL);
                        assertThat(stored.durationMillis()).isEqualTo(42);
                        assertThat(stored.attributes())
                                .containsEntry("token", "[REDACTED]")
                                .containsEntry("email", "[REDACTED]")
                                .containsEntry("status", "SUCCEEDED");
                    });
            assertThat(reader.list("owner-scope-b")).isEmpty();
        } finally {
            database.shutdown();
        }
    }

    @Test
    void doesNotPersistTelemetryWhenObservabilityIsDisabled() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            JdbcStoreTestSupport.createTelemetryTables(jdbc);
            ProductionRuntimeProperties properties = new ProductionRuntimeProperties();
            properties.getObservability().setEnabled(false);
            JdbcRuntimeTelemetry telemetry = new JdbcRuntimeTelemetry(
                    jdbc, new ObjectMapper().findAndRegisterModules(), new SensitiveDataRedactor(), properties);

            telemetry.record(TelemetryEventType.API, "owner-scope-a", "user-a", "agent-a", "api", Duration.ZERO, Map.of());

            assertThat(telemetry.list("owner-scope-a")).isEmpty();
        } finally {
            database.shutdown();
        }
    }
}
