package com.harnessagent.security.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.persistence.JsonColumn;
import com.harnessagent.persistence.DurableStoreCapability;
import com.harnessagent.persistence.DurableBackendType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.harnessagent.security.domain.ResourceType;

@Repository
@Profile("production")
public class JdbcSecurityAuditStore implements SecurityAuditStore, DurableStoreCapability {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcSecurityAuditStore(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, new ObjectMapper().findAndRegisterModules());
    }

    public JdbcSecurityAuditStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public DurableBackendType durableBackendType() {
        return DurableBackendType.MYSQL;
    }

    @Override
    public SecurityAuditRecord save(SecurityAuditRecord record) {
        jdbc.update("""
                insert into ha_security_audit (
                    id, occurred_at, tenant_id, user_id, resource_type, resource_id, action, details_json
                ) values (
                    :id, :occurredAt, :tenantId, :userId, :resourceType, :resourceId, :action, :detailsJson
                )
                """, new MapSqlParameterSource()
                .addValue("id", record.id())
                .addValue("occurredAt", Timestamp.from(record.occurredAt()))
                .addValue("tenantId", record.tenantId())
                .addValue("userId", record.userId())
                .addValue("resourceType", record.resourceType().name())
                .addValue("resourceId", record.resourceId())
                .addValue("action", record.action())
                .addValue("detailsJson", JsonColumn.write(objectMapper, record.sanitizedDetails())));
        return record;
    }

    @Override
    public List<SecurityAuditRecord> search(String tenantId, Instant occurredAtFromInclusive) {
        return jdbc.query("""
                select id, occurred_at, tenant_id, user_id, resource_type, resource_id, action, details_json
                from ha_security_audit
                where tenant_id = :tenantId
                  and occurred_at >= :occurredAtFrom
                order by occurred_at asc, id asc
                """, Map.of(
                        "tenantId", tenantId,
                        "occurredAtFrom", Timestamp.from(occurredAtFromInclusive == null
                                ? Instant.EPOCH
                                : occurredAtFromInclusive)),
                mapper());
    }

    private RowMapper<SecurityAuditRecord> mapper() {
        return (rs, rowNum) -> new SecurityAuditRecord(
                rs.getString("id"),
                instant(rs, "occurred_at"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                ResourceType.valueOf(rs.getString("resource_type")),
                rs.getString("resource_id"),
                rs.getString("action"),
                JsonColumn.read(objectMapper, rs.getString("details_json"), OBJECT_MAP, Map.of()));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }
}
