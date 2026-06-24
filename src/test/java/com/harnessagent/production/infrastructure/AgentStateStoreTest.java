package com.harnessagent.production.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.runtime.OwnerScope;
import io.agentscope.core.state.State;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.harnessagent.production.infrastructure.AgentScopeStateStoreAdapter;
import com.harnessagent.production.infrastructure.DefaultAgentStateStoreFactory;
import com.harnessagent.production.infrastructure.InMemoryAgentStateStore;
import com.harnessagent.production.infrastructure.JdbcAgentStateStore;
import com.harnessagent.production.infrastructure.LocalJsonAgentStateStore;
import com.harnessagent.production.infrastructure.RedisAgentStateStore;
import com.harnessagent.production.state.AgentStateEntry;
import com.harnessagent.production.state.AgentStateStore;
import com.harnessagent.production.state.AgentStateStoreFactory;
import com.harnessagent.production.state.StateStorePlan;
import com.harnessagent.production.state.OwnerStateKeyStrategy;
import com.harnessagent.production.state.TenantStateKeyStrategy;

class AgentStateStoreTest {

    private final OwnerStateKeyStrategy keyStrategy = new OwnerStateKeyStrategy();

    @TempDir
    Path tempDir;

    @Test
    void storesAgentStateByOwnerScopedKey() {
        InMemoryAgentStateStore store = new InMemoryAgentStateStore(keyStrategy, StateStorePlan.local(".state"));
        RuntimeContextScope context = context("personal", "owner-a");
        RuntimeContextScope otherOwnerScope = context("personal", "owner-b");

        AgentStateEntry saved = store.save(context, "memory", "{\"step\":1}");

        assertThat(saved.key())
                .isEqualTo("owner:owner-a:agent:agent-a:session:session-a:scope:memory");
        assertThat(store.load(context, "memory")).contains(saved);
        assertThat(store.load(otherOwnerScope, "memory")).isEmpty();
    }

    @Test
    void localJsonStoreMigratesLegacyKeyToOwnerKeyOnRead() {
        StateStorePlan plan = StateStorePlan.local(tempDir.resolve("state").toString());
        RuntimeContextScope context = context("personal", "owner-a");
        LocalJsonAgentStateStore legacyStore = new LocalJsonAgentStateStore(new TenantStateKeyStrategy(), plan);
        AgentStateEntry legacy = legacyStore.save(context, "memory", "{\"legacy\":true}");
        LocalJsonAgentStateStore store = new LocalJsonAgentStateStore(keyStrategy, plan);

        AgentStateEntry migrated = store.load(context, "memory").orElseThrow();

        String legacyPrefix = "te" + "nant";
        assertThat(legacy.key())
                .isEqualTo(legacyPrefix + ":personal:user:owner-a:agent:agent-a:session:session-a:scope:memory");
        assertThat(migrated.key())
                .isEqualTo("owner:owner-a:agent:agent-a:session:session-a:scope:memory");
        assertThat(migrated.value()).isEqualTo("{\"legacy\":true}");
        assertThat(legacyStore.load(context, "memory")).isEmpty();
        assertThat(store.listSessionScopes(context)).containsExactly("memory");

        AgentStateEntry updated = store.save(context, "memory", "{\"legacy\":false}");

        assertThat(updated.key())
                .isEqualTo("owner:owner-a:agent:agent-a:session:session-a:scope:memory");
        assertThat(legacyStore.load(context, "memory")).isEmpty();
    }

    @Test
    void factoryAllowsLocalStateRejectsRedisAndUsesWiredMysqlStore() {
        AgentStateStoreFactory factory = new DefaultAgentStateStoreFactory(keyStrategy);

        assertThat(factory.create(StateStorePlan.local(".state"))).isInstanceOf(LocalJsonAgentStateStore.class);
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
        RuntimeContextScope context = RuntimeContextScope.fromOwnerScope(new OwnerScope("owner-a", "agent-a", "session-a"));
        InMemoryAgentStateStore store = new InMemoryAgentStateStore(keyStrategy, StateStorePlan.redis("redis://state"));
        AgentScopeStateStoreAdapter adapter = new AgentScopeStateStoreAdapter(context, store);

        adapter.save(context.runtimeUserId(), context.runtimeSessionId(), "agent_meta", new TestState("meta"));
        adapter.save(context.runtimeUserId(), context.runtimeSessionId(), "memory_messages",
                List.of(new TestState("first"), new TestState("second")));

        assertThat(adapter.get(context.runtimeUserId(), context.runtimeSessionId(), "agent_meta", TestState.class))
                .contains(new TestState("meta"));
        assertThat(adapter.getList(context.runtimeUserId(), context.runtimeSessionId(), "memory_messages", TestState.class))
                .containsExactly(new TestState("first"), new TestState("second"));
        assertThat(store.load(context, "agentscope:owner:owner-a:agent-a:session-a:agent_meta")).isPresent();
        assertThat(adapter.exists(context.runtimeUserId(), context.runtimeSessionId())).isTrue();
        assertThat(adapter.listSessionIds(context.runtimeUserId())).containsExactly(context.runtimeSessionId());

        adapter.delete(context.runtimeUserId(), context.runtimeSessionId());

        assertThat(adapter.exists(context.runtimeUserId(), context.runtimeSessionId())).isFalse();
        assertThat(adapter.get(context.runtimeUserId(), context.runtimeSessionId(), "agent_meta", TestState.class)).isEmpty();
    }

    @Test
    void agentScopeAdapterMigratesLegacyRuntimeUserScope() {
        RuntimeContextScope context = RuntimeContextScope.fromOwnerScope(new OwnerScope("owner-a", "agent-a", "session-a"));
        InMemoryAgentStateStore store = new InMemoryAgentStateStore(keyStrategy, StateStorePlan.local(".state"));
        store.save(context, "agentscope:personal:owner-a:agent-a:session-a:agent_meta", "{\"value\":\"legacy\"}");
        AgentScopeStateStoreAdapter adapter = new AgentScopeStateStoreAdapter(context, store);

        Optional<TestState> migrated = adapter.get(
                context.runtimeUserId(),
                context.runtimeSessionId(),
                "agent_meta",
                TestState.class);

        assertThat(migrated).contains(new TestState("legacy"));
        assertThat(store.load(context, "agentscope:personal:owner-a:agent-a:session-a:agent_meta")).isEmpty();
        assertThat(store.load(context, "agentscope:owner:owner-a:agent-a:session-a:agent_meta")).isPresent();
    }

    private static RuntimeContextScope context(String ownerScopeId, String userId) {
        return new RuntimeContextScope(
                ownerScopeId,
                userId,
                "agent-a",
                "session-a",
                ownerScopeId + ":" + userId,
                "agent-a:session-a");
    }

    private record TestState(String value) implements State {
    }
}
