package com.harnessagent.production.infrastructure;

import com.harnessagent.persistence.DurableStoreCapability;
import com.harnessagent.persistence.DurableBackendType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import com.harnessagent.production.budget.BudgetCounter;
import com.harnessagent.production.budget.BudgetCounterStore;

@Repository
@Profile("production")
@ConditionalOnProperty(
        prefix = "harness-agent.production.durable-stores",
        name = "budget-counter",
        havingValue = "redis")
public class RedisBudgetCounterStore implements BudgetCounterStore, DurableStoreCapability {

    private static final String PREFIX = "harness-agent:budget:";

    private final StringRedisTemplate redis;

    public RedisBudgetCounterStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public DurableBackendType durableBackendType() {
        return DurableBackendType.REDIS;
    }

    @Override
    public BudgetCounter increment(String key, long tokens) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
        String counterKey = key.trim();
        String redisKey = PREFIX + counterKey;
        try {
            Long requests = redis.opsForHash().increment(redisKey, "requests", 1);
            Long tokenCount = redis.opsForHash().increment(redisKey, "tokens", Math.max(tokens, 0));
            return new BudgetCounter(
                    counterKey,
                    requests == null ? 0 : requests,
                    tokenCount == null ? 0 : tokenCount);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Redis budget counter increment failed: " + ex.getMessage(), ex);
        }
    }
}
