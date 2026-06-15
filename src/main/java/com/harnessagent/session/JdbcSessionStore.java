package com.harnessagent.session;

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

@Repository
@Profile("production")
public class JdbcSessionStore implements SessionStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSessionStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void appendMessage(RuntimeContextScope context, ChatMessage message) {
        jdbc.update("""
                insert into ha_session_messages (
                    id, tenant_id, user_id, agent_id, session_id, role, content, created_at
                ) values (
                    :id, :tenantId, :userId, :agentId, :sessionId, :role, :content, :createdAt
                )
                """, new MapSqlParameterSource()
                .addValue("id", message.id())
                .addValue("tenantId", context.tenantId())
                .addValue("userId", context.userId())
                .addValue("agentId", context.agentId())
                .addValue("sessionId", context.sessionId())
                .addValue("role", message.role().name())
                .addValue("content", message.content())
                .addValue("createdAt", timestamp(message.createdAt())));
    }

    @Override
    public List<SessionSummary> listSessions(String tenantId, String userId, String agentId) {
        return jdbc.query("""
                select tenant_id, user_id, agent_id, session_id, count(*) as message_count, max(created_at) as last_message_at
                from ha_session_messages
                where tenant_id = :tenantId
                  and user_id = :userId
                  and (:agentId is null or :agentId = '' or agent_id = :agentId)
                group by tenant_id, user_id, agent_id, session_id
                order by last_message_at desc, session_id asc
                """,
                Map.of(
                        "tenantId", tenantId,
                        "userId", userId,
                        "agentId", agentId == null ? "" : agentId),
                summaryMapper());
    }

    @Override
    public List<ChatMessage> listMessages(RuntimeContextScope context) {
        return jdbc.query("""
                select id, role, content, created_at
                from ha_session_messages
                where tenant_id = :tenantId
                  and user_id = :userId
                  and agent_id = :agentId
                  and session_id = :sessionId
                order by created_at asc, id asc
                """, contextParams(context), messageMapper());
    }

    @Override
    public boolean deleteSession(RuntimeContextScope context) {
        return jdbc.update("""
                delete from ha_session_messages
                where tenant_id = :tenantId
                  and user_id = :userId
                  and agent_id = :agentId
                  and session_id = :sessionId
                """, contextParams(context)) > 0;
    }

    private static Map<String, ?> contextParams(RuntimeContextScope context) {
        return Map.of(
                "tenantId", context.tenantId(),
                "userId", context.userId(),
                "agentId", context.agentId(),
                "sessionId", context.sessionId());
    }

    private static RowMapper<ChatMessage> messageMapper() {
        return (rs, rowNum) -> new ChatMessage(
                rs.getString("id"),
                MessageRole.valueOf(rs.getString("role")),
                rs.getString("content"),
                instant(rs, "created_at"));
    }

    private static RowMapper<SessionSummary> summaryMapper() {
        return (rs, rowNum) -> new SessionSummary(
                rs.getString("tenant_id"),
                rs.getString("user_id"),
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
