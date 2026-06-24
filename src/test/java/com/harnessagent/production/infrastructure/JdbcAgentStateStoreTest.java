package com.harnessagent.production.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.runtime.RuntimeContextScope;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import com.harnessagent.production.infrastructure.JdbcAgentStateStore;
import com.harnessagent.production.state.AgentStateEntry;
import com.harnessagent.production.state.StateStorePlan;
import com.harnessagent.production.state.OwnerStateKeyStrategy;
import com.harnessagent.production.state.TenantStateKeyStrategy;

class JdbcAgentStateStoreTest {

    @Test
    void sharesStateAcrossInstancesAndDeletesSessionScopesWithOwnerIsolation() {
        DataSource dataSource = database();
        OwnerStateKeyStrategy keyStrategy = new OwnerStateKeyStrategy();
        JdbcAgentStateStore first = store(dataSource, keyStrategy);
        JdbcAgentStateStore second = store(dataSource, keyStrategy);
        RuntimeContextScope base = context("personal", "owner-a", "agent-a", "session-a");
        RuntimeContextScope otherOwner = context("personal", "owner-b", "agent-a", "session-a");

        first.save(base, "agentscope:runtime-user:runtime-session:memory", "{\"step\":1}");
        first.save(otherOwner, "agentscope:runtime-user:runtime-session:memory", "{\"step\":2}");

        assertThat(second.load(base, "agentscope:runtime-user:runtime-session:memory"))
                .get()
                .extracting(AgentStateEntry::value)
                .isEqualTo("{\"step\":1}");
        assertThat(second.listSessionScopes(base))
                .containsExactly("agentscope:runtime-user:runtime-session:memory");

        assertThat(second.deleteSession(base, "agentscope:runtime-user:runtime-session")).isTrue();

        assertThat(first.load(base, "agentscope:runtime-user:runtime-session:memory")).isEmpty();
        assertThat(first.load(otherOwner, "agentscope:runtime-user:runtime-session:memory")).isPresent();
    }

    @Test
    void migratesLegacyStateKeyToOwnerKeyOnRead() {
        DataSource dataSource = database();
        RuntimeContextScope context = context("personal", "owner-a", "agent-a", "session-a");
        JdbcAgentStateStore legacyStore = store(dataSource, new TenantStateKeyStrategy());
        JdbcAgentStateStore store = store(dataSource, new OwnerStateKeyStrategy());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        AgentStateEntry legacy = legacyStore.save(context, "memory", "{\"legacy\":true}");

        AgentStateEntry migrated = store.load(context, "memory").orElseThrow();

        String legacyPrefix = "te" + "nant";
        assertThat(legacy.key())
                .isEqualTo(legacyPrefix + ":personal:user:owner-a:agent:agent-a:session:session-a:scope:memory");
        assertThat(migrated.key())
                .isEqualTo("owner:owner-a:agent:agent-a:session:session-a:scope:memory");
        assertThat(migrated.value()).isEqualTo("{\"legacy\":true}");
        assertThat(jdbc.queryForObject(
                "select count(*) from ha_agent_state where state_key like ?",
                Integer.class,
                legacyPrefix + ":%")).isZero();
        assertThat(jdbc.queryForObject(
                "select owner_id from ha_agent_state where state_key = ?",
                String.class,
                migrated.key())).isEqualTo("owner-a");

        store.save(context, "memory", "{\"legacy\":false}");

        assertThat(jdbc.queryForObject(
                "select count(*) from ha_agent_state where state_key like ?",
                Integer.class,
                legacyPrefix + ":%")).isZero();
        assertThat(store.load(context, "memory")).get()
                .extracting(AgentStateEntry::value)
                .isEqualTo("{\"legacy\":false}");
    }

    private static JdbcAgentStateStore store(DataSource dataSource, OwnerStateKeyStrategy keyStrategy) {
        return new JdbcAgentStateStore(
                new NamedParameterJdbcTemplate(dataSource),
                keyStrategy,
                StateStorePlan.mysql("jdbc:h2:mem"));
    }

    private static DataSource database() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("classpath:db/migration/V1__durable_persistence.sql")
                .addScript("classpath:db/migration/V3__session_message_content_blocks.sql")
                .addScript("classpath:db/migration/V5__tool_workload_type.sql")
                .addScript("classpath:db/migration/V7__personal_memory_rag_metadata.sql")
                .addScript("classpath:db/migration/V9__personal_tooling_hitl.sql")
                .addScript("classpath:db/migration/V11__owner_scope_persistence.sql")
                .build();
    }

    private static RuntimeContextScope context(String ownerScopeId, String userId, String agentId, String sessionId) {
        return new RuntimeContextScope(ownerScopeId, userId, agentId, sessionId, userId, sessionId);
    }
}
