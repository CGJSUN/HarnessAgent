package com.harnessagent.production;

import com.harnessagent.runtime.RuntimeContextScope;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryAgentStateStore implements AgentStateStore {

    private final TenantStateKeyStrategy keyStrategy;
    private final StateStorePlan plan;
    private final Map<String, AgentStateEntry> entries = new ConcurrentHashMap<>();

    public InMemoryAgentStateStore(TenantStateKeyStrategy keyStrategy, StateStorePlan plan) {
        this.keyStrategy = keyStrategy;
        this.plan = plan;
    }

    @Override
    public StateStorePlan plan() {
        return plan;
    }

    @Override
    public AgentStateEntry save(RuntimeContextScope context, String scope, String value) {
        String key = keyStrategy.key(context, scope);
        AgentStateEntry entry = new AgentStateEntry(
                key,
                context.tenantId(),
                context.userId(),
                context.agentId(),
                context.sessionId(),
                scope,
                value,
                Instant.now());
        entries.put(key, entry);
        return entry;
    }

    @Override
    public Optional<AgentStateEntry> load(RuntimeContextScope context, String scope) {
        return Optional.ofNullable(entries.get(keyStrategy.key(context, scope)));
    }

    @Override
    public boolean delete(RuntimeContextScope context, String scope) {
        return entries.remove(keyStrategy.key(context, scope)) != null;
    }

    @Override
    public boolean exists(RuntimeContextScope context, String sessionScope) {
        String prefix = keyStrategy.key(context, sessionScopePrefix(sessionScope));
        return entries.keySet().stream().anyMatch(key -> key.startsWith(prefix));
    }

    @Override
    public boolean deleteSession(RuntimeContextScope context, String sessionScope) {
        String prefix = keyStrategy.key(context, sessionScopePrefix(sessionScope));
        Set<String> matchingKeys = entries.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .collect(Collectors.toSet());
        matchingKeys.forEach(entries::remove);
        return !matchingKeys.isEmpty();
    }

    @Override
    public Set<String> listSessionScopes(RuntimeContextScope context) {
        String prefix = scopePrefix(context);
        return entries.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .map(key -> key.substring(prefix.length()))
                .collect(Collectors.toUnmodifiableSet());
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
}
