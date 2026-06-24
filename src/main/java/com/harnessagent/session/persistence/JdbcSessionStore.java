package com.harnessagent.session.persistence;

import com.harnessagent.persistence.DurableStoreCapability;
import com.harnessagent.persistence.DurableBackendType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.chat.domain.ContentBlock;
import com.harnessagent.persistence.JsonColumn;
import com.harnessagent.runtime.RuntimeContextScope;
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
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.domain.MessageRole;
import com.harnessagent.session.domain.SessionSummary;

@Repository
@Profile("production")
public class JdbcSessionStore implements SessionStore, DurableStoreCapability {

    private static final TypeReference<List<ContentBlock>> CONTENT_BLOCKS = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcSessionStore(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, new ObjectMapper().findAndRegisterModules());
    }

    public JdbcSessionStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public DurableBackendType durableBackendType() {
        return DurableBackendType.MYSQL;
    }

    @Override
    public void appendMessage(RuntimeContextScope context, ChatMessage message) {
        jdbc.update("""
                insert into ha_session_messages (
                    id, tenant_id, user_id, owner_scope_id, owner_id, agent_id, session_id,
                    role, content, content_blocks_json, created_at
                ) values (
                    :id, :ownerScopeId, :ownerId, :ownerScopeId, :ownerId, :agentId, :sessionId,
                    :role, :content, :contentBlocksJson, :createdAt
                )
                """, new MapSqlParameterSource()
                .addValue("id", message.id())
                .addValue("ownerScopeId", context.ownerScopeId())
                .addValue("ownerId", context.ownerId())
                .addValue("agentId", context.agentId())
                .addValue("sessionId", context.sessionId())
                .addValue("role", message.role().name())
                .addValue("content", message.content())
                .addValue("contentBlocksJson", JsonColumn.write(objectMapper, message.contentBlocks()))
                .addValue("createdAt", timestamp(message.createdAt())));
    }

    @Override
    public List<SessionSummary> listSessions(String ownerScopeId, String ownerId, String agentId) {
        return jdbc.query("""
                select owner_scope_id, owner_id, agent_id, session_id, count(*) as message_count, max(created_at) as last_message_at
                from ha_session_messages
                where owner_scope_id = :ownerScopeId
                  and owner_id = :ownerId
                  and (:agentId is null or :agentId = '' or agent_id = :agentId)
                group by owner_scope_id, owner_id, agent_id, session_id
                order by last_message_at desc, session_id asc
                """,
                Map.of(
                        "ownerScopeId", ownerScopeId,
                        "ownerId", ownerId,
                        "agentId", agentId == null ? "" : agentId),
                summaryMapper());
    }

    @Override
    public List<ChatMessage> listMessages(RuntimeContextScope context) {
        return jdbc.query("""
                select id, role, content, content_blocks_json, created_at
                from ha_session_messages
                where owner_scope_id = :ownerScopeId
                  and owner_id = :ownerId
                  and agent_id = :agentId
                  and session_id = :sessionId
                order by created_at asc, id asc
                """, contextParams(context), messageMapper());
    }

    @Override
    public boolean deleteSession(RuntimeContextScope context) {
        return jdbc.update("""
                delete from ha_session_messages
                where owner_scope_id = :ownerScopeId
                  and owner_id = :ownerId
                  and agent_id = :agentId
                  and session_id = :sessionId
                """, contextParams(context)) > 0;
    }

    private static Map<String, ?> contextParams(RuntimeContextScope context) {
        return Map.of(
                "ownerScopeId", context.ownerScopeId(),
                "ownerId", context.ownerId(),
                "agentId", context.agentId(),
                "sessionId", context.sessionId());
    }

    private RowMapper<ChatMessage> messageMapper() {
        return (rs, rowNum) -> new ChatMessage(
                rs.getString("id"),
                MessageRole.valueOf(rs.getString("role")),
                contentBlocks(rs.getString("content"), rs.getString("content_blocks_json")),
                instant(rs, "created_at"));
    }

    private List<ContentBlock> contentBlocks(String content, String value) {
        List<ContentBlock> blocks = JsonColumn.read(objectMapper, value, CONTENT_BLOCKS, List.of());
        return blocks.isEmpty() ? List.of(ContentBlock.text(content == null ? "" : content)) : blocks;
    }

    private static RowMapper<SessionSummary> summaryMapper() {
        return (rs, rowNum) -> new SessionSummary(
                rs.getString("owner_scope_id"),
                rs.getString("owner_id"),
                rs.getString("agent_id"),
                rs.getString("session_id"),
                rs.getInt("message_count"),
                instant(rs, "last_message_at"));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant == null ? Instant.now() : instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }
}
