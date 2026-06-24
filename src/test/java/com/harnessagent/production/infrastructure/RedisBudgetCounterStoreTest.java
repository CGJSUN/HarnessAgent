package com.harnessagent.production.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.harnessagent.production.budget.BudgetCounter;
import com.harnessagent.production.infrastructure.RedisBudgetCounterStore;

class RedisBudgetCounterStoreTest {

    @Test
    void incrementsRequestAndTokenCountersInRedisHash() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.increment("harness-agent:budget:owner-scope:owner-scope-a", "requests", 1)).thenReturn(2L);
        when(hashes.increment("harness-agent:budget:owner-scope:owner-scope-a", "tokens", 7)).thenReturn(19L);

        BudgetCounter counter = new RedisBudgetCounterStore(redis).increment("owner-scope:owner-scope-a", 7);

        assertThat(counter).isEqualTo(new BudgetCounter("owner-scope:owner-scope-a", 2, 19));
    }

    @Test
    void wrapsRedisIncrementFailures() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.increment("harness-agent:budget:owner-scope:owner-scope-a", "requests", 1))
                .thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> new RedisBudgetCounterStore(redis).increment("owner-scope:owner-scope-a", 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis budget counter increment failed")
                .hasMessageContaining("timeout");
    }
}
