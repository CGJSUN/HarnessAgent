package com.harnessagent.production.infrastructure;

import com.harnessagent.persistence.DurableStoreCapability;
import com.harnessagent.runtime.RuntimeContextScope;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.state.AgentStateEntry;
import com.harnessagent.production.state.AgentStateStore;
import com.harnessagent.production.state.StateStorePlan;
import com.harnessagent.persistence.DurableBackendType;
import com.harnessagent.production.state.TenantStateKeyStrategy;

@Repository
@Profile("production")
@ConditionalOnProperty(
        prefix = "harness-agent.production.state-store",
        name = "type",
        havingValue = "redis")
public class RedisAgentStateStore implements AgentStateStore, DurableStoreCapability {

    private final StringRedisTemplate redis;
    private final TenantStateKeyStrategy keyStrategy;
    private final ProductionRuntimeProperties properties;

    public RedisAgentStateStore(
            StringRedisTemplate redis,
            TenantStateKeyStrategy keyStrategy,
            ProductionRuntimeProperties properties) {
        this.redis = redis;
        this.keyStrategy = keyStrategy;
        this.properties = properties;
    }

    @Override
    public StateStorePlan plan() {
        return StateStorePlan.redis(properties.getStateStore().getRedisUri());
    }

    @Override
    public DurableBackendType durableBackendType() {
        return DurableBackendType.REDIS;
    }

    @Override
    public AgentStateEntry save(RuntimeContextScope context, String scope, String value) {
        AgentStateEntry entry = entry(context, scope, value);
        runRedis("save AgentScope state", () -> redis.opsForValue().set(entry.key(), entry.value()));
        return entry;
    }

    @Override
    public Optional<AgentStateEntry> load(RuntimeContextScope context, String scope) {
        String key = keyStrategy.key(context, scope);
        String value = callRedis("load AgentScope state", () -> redis.opsForValue().get(key));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(new AgentStateEntry(
                key,
                context.tenantId(),
                context.userId(),
                context.agentId(),
                context.sessionId(),
                scope,
                value,
                Instant.now()));
    }

    @Override
    public boolean delete(RuntimeContextScope context, String scope) {
        Boolean deleted = callRedis("delete AgentScope state", () -> redis.delete(keyStrategy.key(context, scope)));
        return Boolean.TRUE.equals(deleted);
    }

    @Override
    public boolean exists(RuntimeContextScope context, String sessionScope) {
        Set<String> keys = keys(context, sessionScope);
        return !keys.isEmpty();
    }

    @Override
    public boolean deleteSession(RuntimeContextScope context, String sessionScope) {
        Set<String> keys = keys(context, sessionScope);
        if (keys.isEmpty()) {
            return false;
        }
        Long deleted = callRedis("delete AgentScope session state", () -> redis.delete(keys));
        return deleted != null && deleted > 0;
    }

    @Override
    public Set<String> listSessionScopes(RuntimeContextScope context) {
        String prefix = scopePrefix(context);
        Set<String> keys = callRedis("list AgentScope state keys", () -> redis.keys(prefix + "*"));
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        return keys.stream()
                .map(key -> key.substring(prefix.length()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> keys(RuntimeContextScope context, String sessionScope) {
        String prefix = keyStrategy.key(context, sessionScopePrefix(sessionScope));
        Set<String> keys = callRedis("list AgentScope session state keys", () -> redis.keys(prefix + "*"));
        return keys == null ? Set.of() : keys;
    }

    private AgentStateEntry entry(RuntimeContextScope context, String scope, String value) {
        return new AgentStateEntry(
                keyStrategy.key(context, scope),
                context.tenantId(),
                context.userId(),
                context.agentId(),
                context.sessionId(),
                scope,
                value,
                Instant.now());
    }

    private static String scopePrefix(RuntimeContextScope context) {
        return String.join(":",
                "tenant", context.tenantId(),
                "user", context.userId(),
                "agent", context.agentId(),
                "session", context.sessionId(),
                "scope", "");
    }

    private static String sessionScopePrefix(String sessionScope) {
        String scope = sessionScope == null || sessionScope.isBlank() ? "default" : sessionScope.trim();
        return scope.endsWith(":") ? scope : scope + ":";
    }

    private static void runRedis(String operation, Runnable runnable) {
        callRedis(operation, () -> {
            runnable.run();
            return null;
        });
    }

    private static <T> T callRedis(String operation, RedisCall<T> call) {
        try {
            return call.execute();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Redis " + operation + " failed: " + ex.getMessage(), ex);
        }
    }

    @FunctionalInterface
    private interface RedisCall<T> {
        T execute();
    }
}
