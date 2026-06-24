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
public class JdbcSecurityActivityStore implements SecurityActivityStore, DurableStoreCapability {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcSecurityActivityStore(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, new ObjectMapper().findAndRegisterModules());
    }

    public JdbcSecurityActivityStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public DurableBackendType durableBackendType() {
        return DurableBackendType.MYSQL;
    }

    @Override
    public SecurityActivityRecord save(SecurityActivityRecord record) {
        jdbc.update("""
                insert into ha_security_activity (
                    id, occurred_at, tenant_id, user_id, owner_scope_id, owner_id,
                    resource_type, resource_id, action, details_json
                ) values (
                    :id, :occurredAt, :ownerScopeId, :ownerId, :ownerScopeId, :ownerId,
                    :resourceType, :resourceId, :action, :detailsJson
                )
                """, new MapSqlParameterSource()
                .addValue("id", record.id())
                .addValue("occurredAt", Timestamp.from(record.occurredAt()))
                .addValue("ownerScopeId", record.ownerScopeId())
                .addValue("ownerId", record.ownerId())
                .addValue("resourceType", record.resourceType().name())
                .addValue("resourceId", record.resourceId())
                .addValue("action", record.action())
                .addValue("detailsJson", JsonColumn.write(objectMapper, record.sanitizedDetails())));
        return record;
    }

    @Override
    public List<SecurityActivityRecord> search(String ownerScopeId, Instant occurredAtFromInclusive) {
        return jdbc.query("""
                select id, occurred_at, owner_scope_id, owner_id, resource_type, resource_id, action, details_json
                from ha_security_activity
                where owner_scope_id = :ownerScopeId
                  and occurred_at >= :occurredAtFrom
                order by occurred_at asc, id asc
                """, Map.of(
                        "ownerScopeId", ownerScopeId,
                        "occurredAtFrom", Timestamp.from(occurredAtFromInclusive == null
                                ? Instant.EPOCH
                                : occurredAtFromInclusive)),
                mapper());
    }

    private RowMapper<SecurityActivityRecord> mapper() {
        return (rs, rowNum) -> new SecurityActivityRecord(
                rs.getString("id"),
                instant(rs, "occurred_at"),
                rs.getString("owner_scope_id"),
                rs.getString("owner_id"),
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
