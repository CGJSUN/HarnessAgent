package com.harnessagent.production.infrastructure;

import com.harnessagent.persistence.DurableStoreCapability;
import com.harnessagent.persistence.DurableBackendType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.harnessagent.production.budget.BudgetCounter;
import com.harnessagent.production.budget.BudgetCounterStore;

@Repository
@Profile("production")
@ConditionalOnProperty(
        prefix = "harness-agent.production.durable-stores",
        name = "budget-counter",
        havingValue = "mysql")
public class JdbcBudgetCounterStore implements BudgetCounterStore, DurableStoreCapability {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcBudgetCounterStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DurableBackendType durableBackendType() {
        return DurableBackendType.MYSQL;
    }

    @Override
    public BudgetCounter increment(String key, long tokens) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
        String counterKey = key.trim();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("counterKey", counterKey)
                .addValue("tokens", Math.max(tokens, 0))
                .addValue("updatedAt", Timestamp.from(Instant.now()));
        int updated = jdbc.update("""
                UPDATE ha_budget_counters
                   SET requests = requests + 1,
                       tokens = tokens + :tokens,
                       updated_at = :updatedAt
                 WHERE counter_key = :counterKey
                """, params);
        if (updated == 0) {
            insertOrIncrement(addDimensions(params));
        }
        return jdbc.queryForObject("""
                SELECT counter_key, requests, tokens
                  FROM ha_budget_counters
                 WHERE counter_key = :counterKey
                """, Map.of("counterKey", counterKey), (rs, rowNum) ->
                new BudgetCounter(rs.getString("counter_key"), rs.getLong("requests"), rs.getLong("tokens")));
    }

    private void insertOrIncrement(MapSqlParameterSource params) {
        try {
            jdbc.update("""
                    INSERT INTO ha_budget_counters
                        (counter_key, tenant_id, user_id, owner_scope_id, owner_id,
                         agent_id, resource_id, requests, tokens, updated_at)
                    VALUES
                        (:counterKey, :ownerScopeId, :ownerId, :ownerScopeId, :ownerId,
                         :agentId, :resourceId, 1, :tokens, :updatedAt)
                    """, params);
        } catch (DuplicateKeyException ignored) {
            jdbc.update("""
                    UPDATE ha_budget_counters
                       SET requests = requests + 1,
                           tokens = tokens + :tokens,
                           updated_at = :updatedAt
                     WHERE counter_key = :counterKey
                    """, params);
        }
    }

    private static MapSqlParameterSource addDimensions(MapSqlParameterSource params) {
        BudgetCounterDimensions dimensions = BudgetCounterDimensions.from((String) params.getValue("counterKey"));
        return params
                .addValue("ownerScopeId", dimensions.ownerScopeId())
                .addValue("ownerId", dimensions.ownerId())
                .addValue("agentId", dimensions.agentId())
                .addValue("resourceId", dimensions.resourceId());
    }

    private record BudgetCounterDimensions(String ownerScopeId, String ownerId, String agentId, String resourceId) {

        static BudgetCounterDimensions from(String key) {
            if (key == null || key.isBlank()) {
                return empty();
            }
            String[] parts = key.split(":");
            if (parts.length >= 2 && "tenant".equals(parts[0])) {
                return new BudgetCounterDimensions(parts[1], "", "", "");
            }
            if (parts.length >= 3 && "user".equals(parts[0])) {
                return new BudgetCounterDimensions(parts[1], parts[2], "", "");
            }
            if (parts.length >= 3 && "agent".equals(parts[0])) {
                return new BudgetCounterDimensions(parts[1], "", parts[2], "");
            }
            if (parts.length >= 2 && "provider".equals(parts[0])) {
                return new BudgetCounterDimensions("", "", "", parts[1]);
            }
            if (parts.length >= 5 && "scope".equals(parts[0])) {
                return new BudgetCounterDimensions(parts[1], parts[2], parts[3], parts[4]);
            }
            return empty();
        }

        private static BudgetCounterDimensions empty() {
            return new BudgetCounterDimensions("", "", "", "");
        }
    }
}
