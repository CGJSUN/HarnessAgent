package com.harnessagent.production.infrastructure;

import com.harnessagent.persistence.DurableStoreCapability;
import com.harnessagent.runtime.RuntimeContextScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.harnessagent.production.state.AgentStateEntry;
import com.harnessagent.production.state.AgentStateStore;
import com.harnessagent.production.state.StateStorePlan;
import com.harnessagent.persistence.DurableBackendType;
import com.harnessagent.production.state.OwnerStateKeyStrategy;

@Repository
@Profile("production")
@ConditionalOnProperty(
        prefix = "harness-agent.production.state-store",
        name = "type",
        havingValue = "mysql")
public class JdbcAgentStateStore implements AgentStateStore, DurableStoreCapability {

    private final NamedParameterJdbcTemplate jdbc;
    private final OwnerStateKeyStrategy keyStrategy;
    private final StateStorePlan plan;

    public JdbcAgentStateStore(NamedParameterJdbcTemplate jdbc, OwnerStateKeyStrategy keyStrategy) {
        this(jdbc, keyStrategy, StateStorePlan.mysql("datasource"));
    }

    public JdbcAgentStateStore(
            NamedParameterJdbcTemplate jdbc,
            OwnerStateKeyStrategy keyStrategy,
            StateStorePlan plan) {
        this.jdbc = jdbc;
        this.keyStrategy = keyStrategy;
        this.plan = plan;
    }

    @Override
    public DurableBackendType durableBackendType() {
        return DurableBackendType.MYSQL;
    }

    @Override
    public StateStorePlan plan() {
        return plan;
    }

    @Override
    public AgentStateEntry save(RuntimeContextScope context, String scope, String value) {
        String key = keyStrategy.key(context, scope);
        AgentStateEntry entry = new AgentStateEntry(
                key,
                context.ownerScopeId(),
                context.ownerId(),
                context.agentId(),
                context.sessionId(),
                keyStrategy.normalizeScope(scope),
                value,
                Instant.now());
        upsert(entry);
        deleteLegacyIfDifferent(entry.key(), context, scope);
        return entry;
    }

    private void upsert(AgentStateEntry entry) {
        MapSqlParameterSource params = params(entry);
        int updated = jdbc.update("""
                update ha_agent_state
                set tenant_id = :ownerScopeId,
                    user_id = :ownerId,
                    owner_scope_id = :ownerScopeId,
                    owner_id = :ownerId,
                    agent_id = :agentId,
                    session_id = :sessionId,
                    scope = :scope,
                    state_value = :stateValue,
                    updated_at = :updatedAt
                where state_key = :stateKey
                """, params);
        if (updated == 0) {
            jdbc.update("""
                    insert into ha_agent_state (
                        state_key, tenant_id, user_id, owner_scope_id, owner_id,
                        agent_id, session_id, scope, state_value, updated_at
                    ) values (
                        :stateKey, :ownerScopeId, :ownerId, :ownerScopeId, :ownerId,
                        :agentId, :sessionId, :scope, :stateValue, :updatedAt
                    )
                    """, params);
        }
    }

    @Override
    public Optional<AgentStateEntry> load(RuntimeContextScope context, String scope) {
        String key = keyStrategy.key(context, scope);
        Optional<AgentStateEntry> ownerEntry = findByKey(key);
        if (ownerEntry.isPresent()) {
            return ownerEntry;
        }
        return findByKey(keyStrategy.legacyKey(context, scope))
                .map(legacy -> migrateLegacyEntry(context, keyStrategy.normalizeScope(scope), legacy));
    }

    private Optional<AgentStateEntry> findByKey(String key) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select state_key, tenant_id, user_id, agent_id, session_id, scope, state_value, updated_at
                    from ha_agent_state
                    where state_key = :stateKey
                    """, Map.of("stateKey", key), mapper()));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public boolean delete(RuntimeContextScope context, String scope) {
        boolean deletedOwner = deleteKey(keyStrategy.key(context, scope));
        boolean deletedLegacy = deleteKey(keyStrategy.legacyKey(context, scope));
        return deletedOwner || deletedLegacy;
    }

    @Override
    public boolean exists(RuntimeContextScope context, String sessionScope) {
        migrateLegacySession(context);
        Integer count = jdbc.queryForObject("""
                select count(*)
                from ha_agent_state
                where owner_scope_id = :ownerScopeId
                  and owner_id = :ownerId
                  and agent_id = :agentId
                  and session_id = :sessionId
                  and scope like :scopePrefix
                """, scopeParams(context, sessionScope), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean deleteSession(RuntimeContextScope context, String sessionScope) {
        migrateLegacySession(context);
        return jdbc.update("""
                delete from ha_agent_state
                where owner_scope_id = :ownerScopeId
                  and owner_id = :ownerId
                  and agent_id = :agentId
                  and session_id = :sessionId
                  and scope like :scopePrefix
                """, scopeParams(context, sessionScope)) > 0;
    }

    @Override
    public Set<String> listSessionScopes(RuntimeContextScope context) {
        migrateLegacySession(context);
        List<String> scopes = jdbc.queryForList("""
                select scope
                from ha_agent_state
                where owner_scope_id = :ownerScopeId
                  and owner_id = :ownerId
                  and agent_id = :agentId
                  and session_id = :sessionId
                order by scope asc
                """, contextParams(context), String.class);
        return scopes.stream().collect(Collectors.toUnmodifiableSet());
    }

    private static MapSqlParameterSource params(AgentStateEntry entry) {
        return new MapSqlParameterSource()
                .addValue("stateKey", entry.key())
                .addValue("ownerScopeId", entry.ownerScopeId())
                .addValue("ownerId", entry.ownerId())
                .addValue("agentId", entry.agentId())
                .addValue("sessionId", entry.sessionId())
                .addValue("scope", entry.scope())
                .addValue("stateValue", entry.value())
                .addValue("updatedAt", Timestamp.from(entry.updatedAt()));
    }

    private static Map<String, ?> contextParams(RuntimeContextScope context) {
        return Map.of(
                "ownerScopeId", context.ownerScopeId(),
                "ownerId", context.ownerId(),
                "agentId", context.agentId(),
                "sessionId", context.sessionId());
    }

    private static Map<String, ?> scopeParams(RuntimeContextScope context, String sessionScope) {
        return Map.of(
                "ownerScopeId", context.ownerScopeId(),
                "ownerId", context.ownerId(),
                "agentId", context.agentId(),
                "sessionId", context.sessionId(),
                "scopePrefix", sessionScopePrefix(sessionScope) + "%");
    }

    private static String sessionScopePrefix(String sessionScope) {
        String scope = sessionScope == null || sessionScope.isBlank() ? "default" : sessionScope.trim();
        return scope.endsWith(":") ? scope : scope + ":";
    }

    private static RowMapper<AgentStateEntry> mapper() {
        return (rs, rowNum) -> new AgentStateEntry(
                rs.getString("state_key"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getString("agent_id"),
                rs.getString("session_id"),
                rs.getString("scope"),
                rs.getString("state_value"),
                instant(rs, "updated_at"));
    }

    private AgentStateEntry migrateLegacyEntry(RuntimeContextScope context, String scope, AgentStateEntry legacy) {
        String normalizedScope = keyStrategy.normalizeScope(scope);
        Optional<AgentStateEntry> existing = findByKey(keyStrategy.key(context, normalizedScope));
        if (existing.isPresent()) {
            deleteKey(legacy.key());
            return existing.get();
        }
        AgentStateEntry migrated = new AgentStateEntry(
                keyStrategy.key(context, normalizedScope),
                context.ownerScopeId(),
                context.ownerId(),
                context.agentId(),
                context.sessionId(),
                normalizedScope,
                legacy.value(),
                legacy.updatedAt());
        upsert(migrated);
        deleteKey(legacy.key());
        return migrated;
    }

    private void migrateLegacySession(RuntimeContextScope context) {
        String legacyPrefix = keyStrategy.legacyScopePrefix(context);
        List<AgentStateEntry> legacyEntries = jdbc.query("""
                select state_key, tenant_id, user_id, agent_id, session_id, scope, state_value, updated_at
                from ha_agent_state
                where tenant_id = :ownerScopeId
                  and user_id = :ownerId
                  and agent_id = :agentId
                  and session_id = :sessionId
                """, contextParams(context), mapper());
        legacyEntries.stream()
                .filter(legacy -> legacy.key().startsWith(legacyPrefix))
                .forEach(legacy -> {
                    String scope = legacy.key().substring(legacyPrefix.length());
                    migrateLegacyEntry(context, scope, legacy);
                });
    }

    private boolean deleteKey(String key) {
        return jdbc.update("""
                delete from ha_agent_state
                where state_key = :stateKey
                """, Map.of("stateKey", key)) > 0;
    }

    private void deleteLegacyIfDifferent(String ownerKey, RuntimeContextScope context, String scope) {
        String legacyKey = keyStrategy.legacyKey(context, scope);
        if (!ownerKey.equals(legacyKey)) {
            deleteKey(legacyKey);
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }
}
