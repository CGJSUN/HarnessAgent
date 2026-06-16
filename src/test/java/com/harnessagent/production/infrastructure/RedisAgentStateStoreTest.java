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
import com.harnessagent.production.state.TenantStateKeyStrategy;

class RedisAgentStateStoreTest {

    private final TenantStateKeyStrategy keyStrategy = new TenantStateKeyStrategy();

    @Test
    void storesListsAndDeletesTenantScopedAgentState() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ProductionRuntimeProperties properties = properties();
        RedisAgentStateStore store = new RedisAgentStateStore(redis, keyStrategy, properties);
        RuntimeContextScope context = context();
        String scope = "agentscope:runtime-user:runtime-session:memory";
        String key = keyStrategy.key(context, scope);
        String sessionPrefix = keyStrategy.key(context, "agentscope:runtime-user:runtime-session:");
        String contextPrefix = "tenant:tenant-a:user:user-a:agent:agent-a:session:session-a:scope:";

        when(values.get(key)).thenReturn("{\"step\":1}");
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
                "tenant-a",
                "user-a",
                "agent-a",
                "session-a",
                "runtime-user",
                "runtime-session");
    }
}
