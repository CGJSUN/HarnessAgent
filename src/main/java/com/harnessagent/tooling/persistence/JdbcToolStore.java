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
import com.harnessagent.production.config.AgentWorkloadType;
import com.harnessagent.tooling.application.ToolService;
import com.harnessagent.tooling.audit.ToolAuditRecord;
import com.harnessagent.tooling.domain.ToolAuditPolicy;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolExecutionStatus;
import com.harnessagent.tooling.domain.ToolOutputSchema;
import com.harnessagent.tooling.domain.ToolParameterSchema;
import com.harnessagent.tooling.domain.ToolPendingConfirmation;
import com.harnessagent.tooling.domain.ToolPendingConfirmationStatus;
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
                    output_schema_json = ?, permission_policy_json = ?, audit_policy_json = ?, workload_type = ?,
                    created_at = ?, updated_at = ?
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
                JsonColumn.write(objectMapper, definition.outputSchema()),
                JsonColumn.write(objectMapper, definition.permissionPolicy()),
                JsonColumn.write(objectMapper, definition.auditPolicy()),
                definition.workloadType().name(),
                timestamp(definition.createdAt()),
                timestamp(definition.updatedAt()),
                definition.id());
        if (updated == 0) {
            jdbc.update("""
                    insert into ha_tool_definitions (
                        id, tenant_id, name, description, owner_system, owner_id, source_type, source_ref,
                        risk_level, mutating, enabled, parameter_schema_json, output_schema_json,
                        permission_policy_json, audit_policy_json, workload_type, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    JsonColumn.write(objectMapper, definition.outputSchema()),
                    JsonColumn.write(objectMapper, definition.permissionPolicy()),
                    JsonColumn.write(objectMapper, definition.auditPolicy()),
                    definition.workloadType().name(),
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
                           risk_level, mutating, enabled, parameter_schema_json, output_schema_json,
                           permission_policy_json, audit_policy_json, workload_type, created_at, updated_at
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
                       risk_level, mutating, enabled, parameter_schema_json, output_schema_json,
                       permission_policy_json, audit_policy_json, workload_type, created_at, updated_at
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

    @Override
    public ToolPendingConfirmation savePendingConfirmation(ToolPendingConfirmation confirmation) {
        int updated = jdbc.update("""
                update ha_tool_pending_confirmations
                set status = ?, parameters_json = ?, sanitized_input_json = ?, operation_summary_json = ?,
                    parameter_fingerprint = ?, idempotency_key = ?, updated_at = ?, expires_at = ?,
                    decided_at = ?, decision_reason = ?
                where confirmation_id = ?
                """,
                confirmation.status().name(),
                JsonColumn.write(objectMapper, confirmation.parameters()),
                JsonColumn.write(objectMapper, confirmation.sanitizedInput()),
                JsonColumn.write(objectMapper, confirmation.operationSummary()),
                confirmation.parameterFingerprint(),
                confirmation.idempotencyKey(),
                timestamp(confirmation.updatedAt()),
                timestamp(confirmation.expiresAt()),
                nullableTimestamp(confirmation.decidedAt()),
                confirmation.decisionReason(),
                confirmation.confirmationId());
        if (updated == 0) {
            jdbc.update("""
                    insert into ha_tool_pending_confirmations (
                        confirmation_id, tenant_id, user_id, agent_id, session_id, tool_id, tool_name,
                        source_type, risk_level, status, parameters_json, sanitized_input_json,
                        operation_summary_json, parameter_fingerprint, idempotency_key, created_at,
                        updated_at, expires_at, decided_at, decision_reason
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    confirmation.confirmationId(),
                    confirmation.tenantId(),
                    confirmation.userId(),
                    confirmation.agentId(),
                    confirmation.sessionId(),
                    confirmation.toolId(),
                    confirmation.toolName(),
                    confirmation.sourceType().name(),
                    confirmation.riskLevel().name(),
                    confirmation.status().name(),
                    JsonColumn.write(objectMapper, confirmation.parameters()),
                    JsonColumn.write(objectMapper, confirmation.sanitizedInput()),
                    JsonColumn.write(objectMapper, confirmation.operationSummary()),
                    confirmation.parameterFingerprint(),
                    confirmation.idempotencyKey(),
                    timestamp(confirmation.createdAt()),
                    timestamp(confirmation.updatedAt()),
                    timestamp(confirmation.expiresAt()),
                    nullableTimestamp(confirmation.decidedAt()),
                    confirmation.decisionReason());
        }
        return confirmation;
    }

    @Override
    public Optional<ToolPendingConfirmation> findPendingConfirmation(String confirmationId) {
        if (confirmationId == null || confirmationId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select confirmation_id, tenant_id, user_id, agent_id, session_id, tool_id, tool_name,
                           source_type, risk_level, status, parameters_json, sanitized_input_json,
                           operation_summary_json, parameter_fingerprint, idempotency_key, created_at,
                           updated_at, expires_at, decided_at, decision_reason
                    from ha_tool_pending_confirmations
                    where confirmation_id = ?
                    """, pendingConfirmationMapper(), confirmationId.trim()));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public boolean claimPendingConfirmation(String confirmationId, String decisionReason) {
        if (confirmationId == null || confirmationId.isBlank()) {
            return false;
        }
        int updated = jdbc.update("""
                update ha_tool_pending_confirmations
                set status = 'CONFIRMED', updated_at = ?, decided_at = ?, decision_reason = ?
                where confirmation_id = ? and status = 'PENDING'
                """,
                timestamp(Instant.now()),
                timestamp(Instant.now()),
                decisionReason == null || decisionReason.isBlank() ? "confirmed" : decisionReason.trim(),
                confirmationId.trim());
        return updated == 1;
    }

    @Override
    public List<ToolPendingConfirmation> listPendingConfirmations(
            String tenantId,
            String userId,
            String agentId,
            String sessionId) {
        return jdbc.query("""
                select confirmation_id, tenant_id, user_id, agent_id, session_id, tool_id, tool_name,
                       source_type, risk_level, status, parameters_json, sanitized_input_json,
                       operation_summary_json, parameter_fingerprint, idempotency_key, created_at,
                       updated_at, expires_at, decided_at, decision_reason
                from ha_tool_pending_confirmations
                where tenant_id = ? and user_id = ? and agent_id = ? and session_id = ? and status = 'PENDING'
                order by created_at asc, confirmation_id asc
                """, pendingConfirmationMapper(), tenantId, userId, agentId, sessionId);
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
                JsonColumn.read(objectMapper, rs.getString("output_schema_json"), ToolOutputSchema.class),
                JsonColumn.read(objectMapper, rs.getString("permission_policy_json"), ToolPermissionPolicy.class),
                JsonColumn.read(objectMapper, rs.getString("audit_policy_json"), ToolAuditPolicy.class),
                AgentWorkloadType.valueOf(rs.getString("workload_type")),
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

    private RowMapper<ToolPendingConfirmation> pendingConfirmationMapper() {
        return (rs, rowNum) -> new ToolPendingConfirmation(
                rs.getString("confirmation_id"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getString("agent_id"),
                rs.getString("session_id"),
                rs.getString("tool_id"),
                rs.getString("tool_name"),
                ToolSourceType.valueOf(rs.getString("source_type")),
                ToolRiskLevel.valueOf(rs.getString("risk_level")),
                ToolPendingConfirmationStatus.valueOf(rs.getString("status")),
                JsonColumn.read(objectMapper, rs.getString("parameters_json"), OBJECT_MAP, Map.of()),
                JsonColumn.read(objectMapper, rs.getString("sanitized_input_json"), OBJECT_MAP, Map.of()),
                JsonColumn.read(objectMapper, rs.getString("operation_summary_json"), OBJECT_MAP, Map.of()),
                rs.getString("parameter_fingerprint"),
                rs.getString("idempotency_key"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"),
                instant(rs, "expires_at"),
                nullableInstant(rs, "decided_at"),
                rs.getString("decision_reason"));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant == null ? Instant.now() : instant);
    }

    private static Timestamp nullableTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
