package com.harnessagent.production.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.persistence.JsonColumn;
import com.harnessagent.persistence.DurableStoreCapability;
import com.harnessagent.persistence.DurableBackendType;
import com.harnessagent.security.application.SensitiveDataRedactor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.telemetry.RuntimeTelemetry;
import com.harnessagent.production.telemetry.TelemetryEvent;
import com.harnessagent.production.telemetry.TelemetryEventType;

@Component
@Profile("production")
public class JdbcRuntimeTelemetry implements RuntimeTelemetry, DurableStoreCapability {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final SensitiveDataRedactor redactor;
    private final boolean enabled;

    public JdbcRuntimeTelemetry(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            SensitiveDataRedactor redactor,
            ProductionRuntimeProperties properties) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.redactor = redactor;
        this.enabled = properties.getObservability().isEnabled();
    }

    @Override
    public DurableBackendType durableBackendType() {
        return DurableBackendType.MYSQL;
    }

    @Override
    public TelemetryEvent record(
            TelemetryEventType type,
            String ownerScopeId,
            String ownerId,
            String agentId,
            String component,
            Duration duration,
            Map<String, Object> attributes) {
        TelemetryEvent event = new TelemetryEvent(
                null,
                Instant.now(),
                type,
                ownerScopeId,
                ownerId,
                agentId,
                component,
                duration == null ? 0 : duration.toMillis(),
                redactor.redactMap(attributes));
        if (enabled) {
            jdbc.update("""
                    insert into ha_telemetry_events (
                        id, occurred_at, type, tenant_id, user_id, owner_scope_id, owner_id,
                        agent_id, component, duration_millis, attributes_json
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    event.id(),
                    Timestamp.from(event.occurredAt()),
                    event.type().name(),
                    event.ownerScopeId(),
                    event.ownerId(),
                    event.ownerScopeId(),
                    event.ownerId(),
                    event.agentId(),
                    event.component(),
                    event.durationMillis(),
                    JsonColumn.write(objectMapper, event.attributes()));
        }
        return event;
    }

    @Override
    public List<TelemetryEvent> list(String ownerScopeId) {
        if (ownerScopeId == null || ownerScopeId.isBlank()) {
            throw new IllegalArgumentException("owner scope is required");
        }
        return jdbc.query("""
                select id, occurred_at, type, owner_scope_id, owner_id, agent_id, component,
                       duration_millis, attributes_json
                from ha_telemetry_events
                where owner_scope_id = ?
                order by occurred_at asc, id asc
                """, telemetryMapper(), ownerScopeId.trim());
    }

    private RowMapper<TelemetryEvent> telemetryMapper() {
        return (rs, rowNum) -> new TelemetryEvent(
                rs.getString("id"),
                instant(rs, "occurred_at"),
                TelemetryEventType.valueOf(rs.getString("type")),
                rs.getString("owner_scope_id"),
                rs.getString("owner_id"),
                rs.getString("agent_id"),
                rs.getString("component"),
                rs.getLong("duration_millis"),
                JsonColumn.read(objectMapper, rs.getString("attributes_json"), OBJECT_MAP, Map.of()));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }
}
