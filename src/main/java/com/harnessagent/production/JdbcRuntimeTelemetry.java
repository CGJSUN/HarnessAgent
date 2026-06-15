package com.harnessagent.production;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.persistence.JsonColumn;
import com.harnessagent.security.SensitiveDataRedactor;
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

@Component
@Profile("production")
public class JdbcRuntimeTelemetry implements RuntimeTelemetry {

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
    public TelemetryEvent record(
            TelemetryEventType type,
            String tenantId,
            String userId,
            String agentId,
            String component,
            Duration duration,
            Map<String, Object> attributes) {
        TelemetryEvent event = new TelemetryEvent(
                null,
                Instant.now(),
                type,
                tenantId,
                userId,
                agentId,
                component,
                duration == null ? 0 : duration.toMillis(),
                redactor.redactMap(attributes));
        if (enabled) {
            jdbc.update("""
                    insert into ha_telemetry_events (
                        id, occurred_at, type, tenant_id, user_id, agent_id, component,
                        duration_millis, attributes_json
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    event.id(),
                    Timestamp.from(event.occurredAt()),
                    event.type().name(),
                    event.tenantId(),
                    event.userId(),
                    event.agentId(),
                    event.component(),
                    event.durationMillis(),
                    JsonColumn.write(objectMapper, event.attributes()));
        }
        return event;
    }

    @Override
    public List<TelemetryEvent> list(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return jdbc.query("""
                select id, occurred_at, type, tenant_id, user_id, agent_id, component,
                       duration_millis, attributes_json
                from ha_telemetry_events
                where tenant_id = ?
                order by occurred_at asc, id asc
                """, telemetryMapper(), tenantId.trim());
    }

    private RowMapper<TelemetryEvent> telemetryMapper() {
        return (rs, rowNum) -> new TelemetryEvent(
                rs.getString("id"),
                instant(rs, "occurred_at"),
                TelemetryEventType.valueOf(rs.getString("type")),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
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
