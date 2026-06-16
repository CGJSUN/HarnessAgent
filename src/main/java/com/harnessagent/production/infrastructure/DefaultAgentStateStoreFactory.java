package com.harnessagent.production.infrastructure;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import com.harnessagent.production.state.AgentStateStore;
import com.harnessagent.production.state.AgentStateStoreFactory;
import com.harnessagent.production.state.StateStorePlan;
import com.harnessagent.production.state.TenantStateKeyStrategy;

@Component
public class DefaultAgentStateStoreFactory implements AgentStateStoreFactory {

    private final TenantStateKeyStrategy keyStrategy;
    private final Optional<JdbcAgentStateStore> mysqlStateStore;
    private final Optional<RedisAgentStateStore> redisStateStore;

    public DefaultAgentStateStoreFactory(TenantStateKeyStrategy keyStrategy) {
        this(keyStrategy, Optional.empty(), Optional.empty());
    }

    DefaultAgentStateStoreFactory(
            TenantStateKeyStrategy keyStrategy,
            Optional<JdbcAgentStateStore> mysqlStateStore,
            Optional<RedisAgentStateStore> redisStateStore) {
        this.keyStrategy = keyStrategy;
        this.mysqlStateStore = mysqlStateStore;
        this.redisStateStore = redisStateStore;
    }

    @Autowired
    public DefaultAgentStateStoreFactory(
            TenantStateKeyStrategy keyStrategy,
            ObjectProvider<JdbcAgentStateStore> mysqlStateStore,
            ObjectProvider<RedisAgentStateStore> redisStateStore) {
        this(
                keyStrategy,
                Optional.ofNullable(mysqlStateStore.getIfAvailable()),
                Optional.ofNullable(redisStateStore.getIfAvailable()));
    }

    @Override
    public AgentStateStore create(StateStorePlan plan) {
        return switch (plan.type()) {
            case LOCAL_JSON -> new InMemoryAgentStateStore(keyStrategy, plan);
            case REDIS -> redisStateStore.orElseThrow(() ->
                    new UnsupportedOperationException("Redis AgentStateStore is not wired yet."));
            case MYSQL -> mysqlStateStore.orElseThrow(() ->
                    new UnsupportedOperationException("MySQL AgentStateStore is not wired yet."));
        };
    }
}
