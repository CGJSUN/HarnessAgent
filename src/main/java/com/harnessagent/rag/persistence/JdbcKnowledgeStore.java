package com.harnessagent.rag.persistence;

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
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import com.harnessagent.rag.domain.KnowledgeChunk;
import com.harnessagent.rag.domain.KnowledgeIndexStatus;
import com.harnessagent.rag.domain.KnowledgeSource;
import com.harnessagent.rag.domain.KnowledgeSourceStatus;
import com.harnessagent.rag.domain.KnowledgeSourceType;
import com.harnessagent.rag.domain.KnowledgeVisibility;
import com.harnessagent.rag.domain.MemoryLayer;
import com.harnessagent.rag.domain.MemoryWriteStatus;
import com.harnessagent.rag.domain.PersonalMemoryRecord;
import com.harnessagent.rag.domain.RagFeedback;
import com.harnessagent.rag.domain.RagMetric;

@Repository
@Profile("production")
public class JdbcKnowledgeStore implements KnowledgeStore, DurableStoreCapability {

    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcKnowledgeStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public DurableBackendType durableBackendType() {
        return DurableBackendType.MYSQL;
    }

    @Override
    public KnowledgeSource saveSource(KnowledgeSource source) {
        int updated = jdbc.update("""
                update ha_knowledge_sources
                set tenant_id = ?, owner_scope_id = ?, owner_id = ?, agent_id = ?, title = ?, version = ?,
                    visibility = ?, allowed_owners_json = ?, allowed_users_json = ?, update_policy = ?, source_type = ?,
                    source_uri = ?, index_status = ?, indexed_at = ?, status = ?, created_at = ?, updated_at = ?
                where id = ?
                """,
                source.ownerScopeId(),
                source.ownerScopeId(),
                source.ownerId(),
                source.agentId(),
                source.title(),
                source.version(),
                source.visibility().name(),
                JsonColumn.write(objectMapper, source.allowedOwnerIds()),
                JsonColumn.write(objectMapper, source.allowedOwnerIds()),
                source.updatePolicy(),
                source.sourceType().name(),
                source.sourceUri(),
                source.indexStatus().name(),
                timestampNullable(source.indexedAt()),
                source.status().name(),
                timestamp(source.createdAt()),
                timestamp(source.updatedAt()),
                source.id());
        if (updated == 0) {
            jdbc.update("""
                    insert into ha_knowledge_sources (
                        id, tenant_id, owner_scope_id, owner_id, agent_id, title, version, visibility,
                        allowed_owners_json, allowed_departments_json, allowed_roles_json, allowed_users_json,
                        update_policy, source_type, source_uri,
                        index_status, indexed_at, status, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    source.id(),
                    source.ownerScopeId(),
                    source.ownerScopeId(),
                    source.ownerId(),
                    source.agentId(),
                    source.title(),
                    source.version(),
                    source.visibility().name(),
                    JsonColumn.write(objectMapper, source.allowedOwnerIds()),
                    "[]",
                    "[]",
                    JsonColumn.write(objectMapper, source.allowedOwnerIds()),
                    source.updatePolicy(),
                    source.sourceType().name(),
                    source.sourceUri(),
                    source.indexStatus().name(),
                    timestampNullable(source.indexedAt()),
                    source.status().name(),
                    timestamp(source.createdAt()),
                    timestamp(source.updatedAt()));
        }
        return source;
    }

    @Override
    public Optional<KnowledgeSource> findSource(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, owner_scope_id, owner_id, agent_id, title, version, visibility, allowed_owners_json,
                           update_policy, source_type, source_uri,
                           index_status, indexed_at, status, created_at, updated_at
                    from ha_knowledge_sources
                    where id = ?
                    """, sourceMapper(), sourceId.trim()));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public List<KnowledgeSource> listSources(String ownerScopeId) {
        return jdbc.query("""
                select id, owner_scope_id, owner_id, agent_id, title, version, visibility, allowed_owners_json,
                       update_policy, source_type, source_uri,
                       index_status, indexed_at, status, created_at, updated_at
                from ha_knowledge_sources
                where owner_scope_id = ?
                order by updated_at desc, id asc
                """, sourceMapper(), ownerScopeId);
    }

    @Override
    public void saveChunks(String sourceId, List<KnowledgeChunk> chunks) {
        removeChunks(sourceId);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                insert into ha_knowledge_chunks (
                    id, source_id, tenant_id, owner_scope_id, title, version, chunk_index, content,
                    tokens_json, source_type, source_uri
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, chunks.stream()
                .map(chunk -> new Object[] {
                        chunk.id(),
                        chunk.sourceId(),
                        chunk.ownerScopeId(),
                        chunk.ownerScopeId(),
                        chunk.title(),
                        chunk.version(),
                        chunk.chunkIndex(),
                        chunk.content(),
                        JsonColumn.write(objectMapper, chunk.tokens()),
                        chunk.sourceType().name(),
                        chunk.sourceUri()
                })
                .toList());
    }

    @Override
    public List<KnowledgeChunk> listChunks(String ownerScopeId) {
        return jdbc.query("""
                select id, source_id, owner_scope_id, title, version, chunk_index, content, tokens_json, source_type, source_uri
                from ha_knowledge_chunks
                where owner_scope_id = ?
                order by source_id asc, chunk_index asc
                """, chunkMapper(), ownerScopeId);
    }

    @Override
    public void removeChunks(String sourceId) {
        jdbc.update("delete from ha_knowledge_chunks where source_id = ?", sourceId);
    }

    @Override
    public void recordMetric(RagMetric metric) {
        jdbc.update("""
                insert into ha_rag_metrics (
                    tenant_id, user_id, owner_scope_id, owner_id, query_text, hit, candidate_count, permitted_count,
                    failure_reason, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                metric.ownerScopeId(),
                metric.ownerId(),
                metric.ownerScopeId(),
                metric.ownerId(),
                metric.query(),
                metric.hit(),
                metric.candidateCount(),
                metric.permittedCount(),
                metric.failureReason(),
                timestamp(metric.createdAt()));
    }

    @Override
    public List<RagMetric> listMetrics(String ownerScopeId) {
        return jdbc.query("""
                select owner_scope_id, owner_id, query_text, hit, candidate_count, permitted_count, failure_reason, created_at
                from ha_rag_metrics
                where owner_scope_id = ?
                order by created_at asc, id asc
                """, metricMapper(), ownerScopeId);
    }

    @Override
    public void recordFeedback(RagFeedback feedback) {
        jdbc.update("""
                insert into ha_rag_feedback (
                    tenant_id, user_id, owner_scope_id, owner_id, query_text, helpful, comment_text, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                feedback.ownerScopeId(),
                feedback.ownerId(),
                feedback.ownerScopeId(),
                feedback.ownerId(),
                feedback.query(),
                feedback.helpful(),
                feedback.comment(),
                timestamp(feedback.createdAt()));
    }

    @Override
    public List<RagFeedback> listFeedback(String ownerScopeId) {
        return jdbc.query("""
                select owner_scope_id, owner_id, query_text, helpful, comment_text, created_at
                from ha_rag_feedback
                where owner_scope_id = ?
                order by created_at asc, id asc
                """, feedbackMapper(), ownerScopeId);
    }

    @Override
    public PersonalMemoryRecord saveMemory(PersonalMemoryRecord memory) {
        int updated = jdbc.update("""
                update ha_personal_memories
                set tenant_id = ?, owner_scope_id = ?, owner_id = ?, agent_id = ?, session_id = ?, layer_name = ?,
                    title = ?, content = ?, status = ?, source_id = ?, created_at = ?, updated_at = ?
                where id = ?
                """,
                memory.ownerScopeId(),
                memory.ownerScopeId(),
                memory.ownerId(),
                memory.agentId(),
                memory.sessionId(),
                memory.layer().name(),
                memory.title(),
                memory.content(),
                memory.status().name(),
                memory.sourceId().orElse(""),
                timestamp(memory.createdAt()),
                timestamp(memory.updatedAt()),
                memory.id());
        if (updated == 0) {
            jdbc.update("""
                    insert into ha_personal_memories (
                        id, tenant_id, owner_scope_id, owner_id, agent_id, session_id, layer_name, title, content,
                        status, source_id, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    memory.id(),
                    memory.ownerScopeId(),
                    memory.ownerScopeId(),
                    memory.ownerId(),
                    memory.agentId(),
                    memory.sessionId(),
                    memory.layer().name(),
                    memory.title(),
                    memory.content(),
                    memory.status().name(),
                    memory.sourceId().orElse(""),
                    timestamp(memory.createdAt()),
                    timestamp(memory.updatedAt()));
        }
        return memory;
    }

    @Override
    public Optional<PersonalMemoryRecord> findMemory(String memoryId) {
        if (memoryId == null || memoryId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, owner_scope_id, owner_id, agent_id, session_id, layer_name, title, content,
                           status, source_id, created_at, updated_at
                    from ha_personal_memories
                    where id = ?
                    """, memoryMapper(), memoryId.trim()));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public List<PersonalMemoryRecord> listMemories(String ownerScopeId, String ownerId, String agentId) {
        return jdbc.query("""
                select id, owner_scope_id, owner_id, agent_id, session_id, layer_name, title, content,
                       status, source_id, created_at, updated_at
                from ha_personal_memories
                where owner_scope_id = ? and owner_id = ? and agent_id = ?
                order by updated_at desc, id asc
                """, memoryMapper(), ownerScopeId, ownerId, agentId);
    }

    private RowMapper<KnowledgeSource> sourceMapper() {
        return (rs, rowNum) -> new KnowledgeSource(
                rs.getString("id"),
                rs.getString("owner_scope_id"),
                rs.getString("owner_id"),
                rs.getString("agent_id"),
                rs.getString("title"),
                rs.getString("version"),
                KnowledgeVisibility.valueOf(rs.getString("visibility")),
                JsonColumn.read(objectMapper, rs.getString("allowed_owners_json"), STRING_SET, Set.of()),
                rs.getString("update_policy"),
                KnowledgeSourceType.valueOf(rs.getString("source_type")),
                rs.getString("source_uri"),
                KnowledgeIndexStatus.valueOf(rs.getString("index_status")),
                instantNullable(rs, "indexed_at"),
                KnowledgeSourceStatus.valueOf(rs.getString("status")),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private RowMapper<KnowledgeChunk> chunkMapper() {
        return (rs, rowNum) -> new KnowledgeChunk(
                rs.getString("id"),
                rs.getString("source_id"),
                rs.getString("owner_scope_id"),
                rs.getString("title"),
                rs.getString("version"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                JsonColumn.read(objectMapper, rs.getString("tokens_json"), STRING_SET, Set.of()),
                KnowledgeSourceType.valueOf(rs.getString("source_type")),
                rs.getString("source_uri"));
    }

    private static RowMapper<RagMetric> metricMapper() {
        return (rs, rowNum) -> new RagMetric(
                rs.getString("owner_scope_id"),
                rs.getString("owner_id"),
                rs.getString("query_text"),
                rs.getBoolean("hit"),
                rs.getInt("candidate_count"),
                rs.getInt("permitted_count"),
                rs.getString("failure_reason"),
                instant(rs, "created_at"));
    }

    private static RowMapper<RagFeedback> feedbackMapper() {
        return (rs, rowNum) -> new RagFeedback(
                rs.getString("owner_scope_id"),
                rs.getString("owner_id"),
                rs.getString("query_text"),
                rs.getBoolean("helpful"),
                rs.getString("comment_text"),
                instant(rs, "created_at"));
    }

    private static RowMapper<PersonalMemoryRecord> memoryMapper() {
        return (rs, rowNum) -> new PersonalMemoryRecord(
                rs.getString("id"),
                rs.getString("owner_scope_id"),
                rs.getString("owner_id"),
                rs.getString("agent_id"),
                rs.getString("session_id"),
                MemoryLayer.valueOf(rs.getString("layer_name")),
                rs.getString("title"),
                rs.getString("content"),
                MemoryWriteStatus.valueOf(rs.getString("status")),
                optional(rs.getString("source_id")),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant == null ? Instant.now() : instant);
    }

    private static Timestamp timestampNullable(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private static Instant instantNullable(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
