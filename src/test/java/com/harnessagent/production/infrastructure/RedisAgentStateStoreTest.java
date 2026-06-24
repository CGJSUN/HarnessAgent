package com.harnessagent.production.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.harnessagent.runtime.RuntimeContextScope;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.infrastructure.RedisAgentStateStore;
import com.harnessagent.production.state.AgentStateEntry;
import com.harnessagent.production.state.OwnerStateKeyStrategy;

class RedisAgentStateStoreTest {

    private final OwnerStateKeyStrategy keyStrategy = new OwnerStateKeyStrategy();

    @Test
    void storesListsAndDeletesOwnerScopedAgentState() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ProductionRuntimeProperties properties = properties();
        RedisAgentStateStore store = new RedisAgentStateStore(redis, keyStrategy, properties);
        RuntimeContextScope context = context();
        String scope = "agentscope:runtime-user:runtime-session:memory";
        String key = keyStrategy.key(context, scope);
        String legacyKey = keyStrategy.legacyKey(context, scope);
        String sessionPrefix = keyStrategy.sessionScopePrefix(context, "agentscope:runtime-user:runtime-session");
        String contextPrefix = keyStrategy.scopePrefix(context);
        String legacyContextPrefix = keyStrategy.legacyScopePrefix(context);

        when(values.get(key)).thenReturn("{\"step\":1}");
        when(redis.delete(legacyKey)).thenReturn(false);
        when(redis.keys(legacyContextPrefix + "*")).thenReturn(Set.of());
        when(redis.keys(sessionPrefix + "*")).thenReturn(Set.of(key));
        when(redis.keys(contextPrefix + "*")).thenReturn(Set.of(key));
        when(redis.delete(Set.of(key))).thenReturn(1L);
        when(redis.delete(key)).thenReturn(true);

        AgentStateEntry saved = store.save(context, scope, "{\"step\":1}");

        assertThat(saved.key()).isEqualTo(key);
        verify(values).set(key, "{\"step\":1}");
        assertThat(store.load(context, scope)).get().extracting(AgentStateEntry::value).isEqualTo("{\"step\":1}");
        assertThat(store.exists(context, "agentscope:runtime-user:runtime-session")).isTrue();
        assertThat(store.listSessionScopes(context)).containsExactly(scope);
        assertThat(store.deleteSession(context, "agentscope:runtime-user:runtime-session")).isTrue();
        assertThat(store.delete(context, scope)).isTrue();
    }

    @Test
    void migratesLegacyRedisKeyToOwnerKeyOnRead() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        RedisAgentStateStore store = new RedisAgentStateStore(redis, keyStrategy, properties());
        RuntimeContextScope context = context();
        String scope = "memory";
        String key = keyStrategy.key(context, scope);
        String legacyKey = keyStrategy.legacyKey(context, scope);
        when(values.get(key)).thenReturn(null);
        when(values.get(legacyKey)).thenReturn("{\"legacy\":true}");
        when(redis.delete(legacyKey)).thenReturn(true);

        AgentStateEntry migrated = store.load(context, scope).orElseThrow();

        assertThat(migrated.key()).isEqualTo("owner:user-a:agent:agent-a:session:session-a:scope:memory");
        assertThat(migrated.value()).isEqualTo("{\"legacy\":true}");
        verify(values).set(key, "{\"legacy\":true}");
        verify(redis).delete(legacyKey);
    }

    @Test
    void wrapsRedisFailuresWithControlledMessage() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doThrow(new IllegalStateException("connection refused")).when(values).set(
                keyStrategy.key(context(), "memory"),
                "value");
        RedisAgentStateStore store = new RedisAgentStateStore(redis, keyStrategy, properties());

        assertThatThrownBy(() -> store.save(context(), "memory", "value"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis save AgentScope state failed")
                .hasMessageContaining("connection refused");
    }

    private static ProductionRuntimeProperties properties() {
        ProductionRuntimeProperties properties = new ProductionRuntimeProperties();
        properties.getStateStore().setRedisUri("redis://localhost:6379/0");
        return properties;
    }

    private static RuntimeContextScope context() {
        return new RuntimeContextScope(
                "owner-scope-a",
                "user-a",
                "agent-a",
                "session-a",
                "runtime-user",
                "runtime-session");
    }
}
