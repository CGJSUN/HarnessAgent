package com.harnessagent.production.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.harnessagent.runtime.RuntimeContextScope;
import io.agentscope.core.state.State;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.harnessagent.production.infrastructure.AgentScopeStateStoreAdapter;
import com.harnessagent.production.infrastructure.DefaultAgentStateStoreFactory;
import com.harnessagent.production.infrastructure.InMemoryAgentStateStore;
import com.harnessagent.production.infrastructure.JdbcAgentStateStore;
import com.harnessagent.production.infrastructure.RedisAgentStateStore;
import com.harnessagent.production.state.AgentStateEntry;
import com.harnessagent.production.state.AgentStateStore;
import com.harnessagent.production.state.AgentStateStoreFactory;
import com.harnessagent.production.state.StateStorePlan;
import com.harnessagent.production.state.TenantStateKeyStrategy;

class AgentStateStoreTest {

    private final TenantStateKeyStrategy keyStrategy = new TenantStateKeyStrategy();

    @Test
    void storesAgentStateByTenantScopedKey() {
        InMemoryAgentStateStore store = new InMemoryAgentStateStore(keyStrategy, StateStorePlan.local(".state"));
        RuntimeContextScope context = context("tenant-a", "user-a");
        RuntimeContextScope otherTenant = context("tenant-b", "user-a");

        AgentStateEntry saved = store.save(context, "memory", "{\"step\":1}");

        assertThat(saved.key())
                .isEqualTo("tenant:tenant-a:user:user-a:agent:agent-a:session:session-a:scope:memory");
        assertThat(store.load(context, "memory")).contains(saved);
        assertThat(store.load(otherTenant, "memory")).isEmpty();
    }

    @Test
    void factoryAllowsLocalStateRejectsRedisAndUsesWiredMysqlStore() {
        AgentStateStoreFactory factory = new DefaultAgentStateStoreFactory(keyStrategy);

        assertThat(factory.create(StateStorePlan.local(".state"))).isInstanceOf(InMemoryAgentStateStore.class);
        assertThatThrownBy(() -> factory.create(StateStorePlan.mysql("jdbc:mysql://localhost/harness_agent")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("MySQL AgentStateStore is not wired");
        assertThatThrownBy(() -> factory.create(StateStorePlan.redis("redis://localhost:6379/0")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Redis AgentStateStore is not wired");

        JdbcAgentStateStore mysqlStore = mock(JdbcAgentStateStore.class);
        RedisAgentStateStore redisStore = mock(RedisAgentStateStore.class);
        AgentStateStoreFactory mysqlFactory = new DefaultAgentStateStoreFactory(
                keyStrategy,
                Optional.of(mysqlStore),
                Optional.of(redisStore));

        assertThat(mysqlFactory.create(StateStorePlan.mysql("jdbc:mysql://localhost/harness_agent")))
                .isSameAs(mysqlStore);
        assertThat(mysqlFactory.create(StateStorePlan.redis("redis://localhost:6379/0")))
                .isSameAs(redisStore);
    }

    @Test
    void agentScopeAdapterPersistsSingleValuesAndListsThroughAgentStateStore() {
        RuntimeContextScope context = context("tenant-a", "user-a");
        InMemoryAgentStateStore store = new InMemoryAgentStateStore(keyStrategy, StateStorePlan.redis("redis://state"));
        AgentScopeStateStoreAdapter adapter = new AgentScopeStateStoreAdapter(context, store);

        adapter.save("runtime-user-a", "agent-session-a", "agent_meta", new TestState("meta"));
        adapter.save("runtime-user-a", "agent-session-a", "memory_messages",
                List.of(new TestState("first"), new TestState("second")));

        assertThat(adapter.get("runtime-user-a", "agent-session-a", "agent_meta", TestState.class))
                .contains(new TestState("meta"));
        assertThat(adapter.getList("runtime-user-a", "agent-session-a", "memory_messages", TestState.class))
                .containsExactly(new TestState("first"), new TestState("second"));
        assertThat(store.load(context, "agentscope:runtime-user-a:agent-session-a:agent_meta")).isPresent();
        assertThat(adapter.exists("runtime-user-a", "agent-session-a")).isTrue();
        assertThat(adapter.listSessionIds("runtime-user-a")).containsExactly("agent-session-a");

        adapter.delete("runtime-user-a", "agent-session-a");

        assertThat(adapter.exists("runtime-user-a", "agent-session-a")).isFalse();
        assertThat(adapter.get("runtime-user-a", "agent-session-a", "agent_meta", TestState.class)).isEmpty();
    }

    private static RuntimeContextScope context(String tenantId, String userId) {
        return new RuntimeContextScope(
                tenantId,
                userId,
                "agent-a",
                "session-a",
                tenantId + ":" + userId,
                "agent-a:session-a");
    }

    private record TestState(String value) implements State {
    }
}
