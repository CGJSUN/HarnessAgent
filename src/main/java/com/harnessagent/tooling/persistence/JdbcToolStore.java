package com.harnessagent.tooling.persistence;

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
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import com.harnessagent.tooling.application.ToolService;
import com.harnessagent.tooling.audit.ToolAuditRecord;
import com.harnessagent.tooling.domain.ToolAuditPolicy;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolExecutionStatus;
import com.harnessagent.tooling.domain.ToolParameterSchema;
import com.harnessagent.tooling.domain.ToolPermissionPolicy;
import com.harnessagent.tooling.domain.ToolRiskLevel;
import com.harnessagent.tooling.domain.ToolSourceType;
import com.harnessagent.tooling.execution.ToolExecutionResult;

@Repository
@Profile("production")
public class JdbcToolStore implements ToolStore, DurableStoreCapability {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcToolStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public DurableBackendType durableBackendType() {
        return DurableBackendType.MYSQL;
    }

    @Override
    public ToolDefinition saveTool(ToolDefinition definition) {
        int updated = jdbc.update("""
                update ha_tool_definitions
                set tenant_id = ?, name = ?, description = ?, owner_system = ?, owner_id = ?, source_type = ?,
                    source_ref = ?, risk_level = ?, mutating = ?, enabled = ?, parameter_schema_json = ?,
                    permission_policy_json = ?, audit_policy_json = ?, created_at = ?, updated_at = ?
                where id = ?
                """,
                definition.tenantId(),
                definition.name(),
                definition.description(),
                definition.ownerSystem(),
                definition.ownerId(),
                definition.sourceType().name(),
                definition.sourceRef(),
                definition.riskLevel().name(),
                definition.mutating(),
                definition.enabled(),
                JsonColumn.write(objectMapper, definition.parameterSchema()),
                JsonColumn.write(objectMapper, definition.permissionPolicy()),
                JsonColumn.write(objectMapper, definition.auditPolicy()),
                timestamp(definition.createdAt()),
                timestamp(definition.updatedAt()),
                definition.id());
        if (updated == 0) {
            jdbc.update("""
                    insert into ha_tool_definitions (
                        id, tenant_id, name, description, owner_system, owner_id, source_type, source_ref,
                        risk_level, mutating, enabled, parameter_schema_json, permission_policy_json,
                        audit_policy_json, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    definition.id(),
                    definition.tenantId(),
                    definition.name(),
                    definition.description(),
                    definition.ownerSystem(),
                    definition.ownerId(),
                    definition.sourceType().name(),
                    definition.sourceRef(),
                    definition.riskLevel().name(),
                    definition.mutating(),
                    definition.enabled(),
                    JsonColumn.write(objectMapper, definition.parameterSchema()),
                    JsonColumn.write(objectMapper, definition.permissionPolicy()),
                    JsonColumn.write(objectMapper, definition.auditPolicy()),
                    timestamp(definition.createdAt()),
                    timestamp(definition.updatedAt()));
        }
        return definition;
    }

    @Override
    public Optional<ToolDefinition> findTool(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, tenant_id, name, description, owner_system, owner_id, source_type, source_ref,
                           risk_level, mutating, enabled, parameter_schema_json, permission_policy_json,
                           audit_policy_json, created_at, updated_at
                    from ha_tool_definitions
                    where id = ?
                    """, toolMapper(), toolId.trim()));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public List<ToolDefinition> listTools(String tenantId) {
        return jdbc.query("""
                select id, tenant_id, name, description, owner_system, owner_id, source_type, source_ref,
                       risk_level, mutating, enabled, parameter_schema_json, permission_policy_json,
                       audit_policy_json, created_at, updated_at
                from ha_tool_definitions
                where tenant_id = ?
                order by name asc, id asc
                """, toolMapper(), tenantId);
    }

    @Override
    public void saveAudit(ToolAuditRecord record) {
        jdbc.update("""
                insert into ha_tool_audit_records (
                    id, occurred_at, tenant_id, user_id, agent_id, session_id, tool_id, tool_name, source_type,
                    risk_level, status, sanitized_input_json, sanitized_output_json, duration_millis,
                    approval_id, reviewer_id, idempotency_key, failure_reason
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.id(),
                timestamp(record.occurredAt()),
                record.tenantId(),
                record.userId(),
                record.agentId(),
                record.sessionId(),
                record.toolId(),
                record.toolName(),
                record.sourceType().name(),
                record.riskLevel().name(),
                record.status().name(),
                JsonColumn.write(objectMapper, record.sanitizedInput()),
                JsonColumn.write(objectMapper, record.sanitizedOutput()),
                record.durationMillis(),
                record.approvalId(),
                record.reviewerId(),
                record.idempotencyKey(),
                record.failureReason());
    }

    @Override
    public List<ToolAuditRecord> listAudit(String tenantId) {
        return jdbc.query("""
                select id, occurred_at, tenant_id, user_id, agent_id, session_id, tool_id, tool_name, source_type,
                       risk_level, status, sanitized_input_json, sanitized_output_json, duration_millis,
                       approval_id, reviewer_id, idempotency_key, failure_reason
                from ha_tool_audit_records
                where tenant_id = ?
                order by occurred_at asc, id asc
                """, auditMapper(), tenantId);
    }

    @Override
    public Optional<ToolIdempotencyRecord> findIdempotentResult(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select idempotency_key, parameter_fingerprint, result_json
                    from ha_tool_idempotency_records
                    where idempotency_key = ?
                    """, idempotencyMapper(), idempotencyKey.trim()));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void saveIdempotentResult(String idempotencyKey, String parameterFingerprint, ToolExecutionResult result) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        try {
            jdbc.update("""
                    insert into ha_tool_idempotency_records (
                        idempotency_key, parameter_fingerprint, result_json, created_at
                    ) values (?, ?, ?, ?)
                    """,
                    idempotencyKey.trim(),
                    parameterFingerprint,
                    JsonColumn.write(objectMapper, result),
                    timestamp(Instant.now()));
        } catch (DataIntegrityViolationException ignored) {
            // Preserve the first result for a key. ToolService will return it on the next lookup.
        }
    }

    private RowMapper<ToolDefinition> toolMapper() {
        return (rs, rowNum) -> new ToolDefinition(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("owner_system"),
                rs.getString("owner_id"),
                ToolSourceType.valueOf(rs.getString("source_type")),
                rs.getString("source_ref"),
                ToolRiskLevel.valueOf(rs.getString("risk_level")),
                rs.getBoolean("mutating"),
                rs.getBoolean("enabled"),
                JsonColumn.read(objectMapper, rs.getString("parameter_schema_json"), ToolParameterSchema.class),
                JsonColumn.read(objectMapper, rs.getString("permission_policy_json"), ToolPermissionPolicy.class),
                JsonColumn.read(objectMapper, rs.getString("audit_policy_json"), ToolAuditPolicy.class),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private RowMapper<ToolAuditRecord> auditMapper() {
        return (rs, rowNum) -> new ToolAuditRecord(
                rs.getString("id"),
                instant(rs, "occurred_at"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getString("agent_id"),
                rs.getString("session_id"),
                rs.getString("tool_id"),
                rs.getString("tool_name"),
                ToolSourceType.valueOf(rs.getString("source_type")),
                ToolRiskLevel.valueOf(rs.getString("risk_level")),
                ToolExecutionStatus.valueOf(rs.getString("status")),
                JsonColumn.read(objectMapper, rs.getString("sanitized_input_json"), OBJECT_MAP, Map.of()),
                JsonColumn.read(objectMapper, rs.getString("sanitized_output_json"), OBJECT_MAP, Map.of()),
                rs.getLong("duration_millis"),
                rs.getString("approval_id"),
                rs.getString("reviewer_id"),
                rs.getString("idempotency_key"),
                rs.getString("failure_reason"));
    }

    private RowMapper<ToolIdempotencyRecord> idempotencyMapper() {
        return (rs, rowNum) -> new ToolIdempotencyRecord(
                rs.getString("idempotency_key"),
                rs.getString("parameter_fingerprint"),
                JsonColumn.read(objectMapper, rs.getString("result_json"), ToolExecutionResult.class));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant == null ? Instant.now() : instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }
}
