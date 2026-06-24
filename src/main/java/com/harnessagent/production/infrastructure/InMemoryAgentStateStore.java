package com.harnessagent.production.infrastructure;

import com.harnessagent.runtime.RuntimeContextScope;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.harnessagent.production.state.AgentStateEntry;
import com.harnessagent.production.state.AgentStateStore;
import com.harnessagent.production.state.OwnerStateKeyStrategy;
import com.harnessagent.production.state.StateStorePlan;

public class InMemoryAgentStateStore implements AgentStateStore {

    private final OwnerStateKeyStrategy keyStrategy;
    private final StateStorePlan plan;
    private final Map<String, AgentStateEntry> entries = new ConcurrentHashMap<>();

    public InMemoryAgentStateStore(OwnerStateKeyStrategy keyStrategy, StateStorePlan plan) {
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
                context.ownerScopeId(),
                context.ownerId(),
                context.agentId(),
                context.sessionId(),
                keyStrategy.normalizeScope(scope),
                value,
                Instant.now());
        removeLegacyIfDifferent(entry.key(), context, scope);
        entries.put(key, entry);
        return entry;
    }

    @Override
    public Optional<AgentStateEntry> load(RuntimeContextScope context, String scope) {
        String key = keyStrategy.key(context, scope);
        AgentStateEntry entry = entries.get(key);
        if (entry != null) {
            return Optional.of(entry);
        }
        return migrateLegacyEntry(context, keyStrategy.normalizeScope(scope));
    }

    @Override
    public boolean delete(RuntimeContextScope context, String scope) {
        boolean deletedOwner = entries.remove(keyStrategy.key(context, scope)) != null;
        boolean deletedLegacy = entries.remove(keyStrategy.legacyKey(context, scope)) != null;
        return deletedOwner || deletedLegacy;
    }

    @Override
    public boolean exists(RuntimeContextScope context, String sessionScope) {
        migrateLegacySession(context);
        String prefix = keyStrategy.sessionScopePrefix(context, sessionScope);
        return entries.keySet().stream().anyMatch(key -> key.startsWith(prefix));
    }

    @Override
    public boolean deleteSession(RuntimeContextScope context, String sessionScope) {
        migrateLegacySession(context);
        String prefix = keyStrategy.sessionScopePrefix(context, sessionScope);
        Set<String> matchingKeys = entries.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .collect(Collectors.toSet());
        matchingKeys.forEach(entries::remove);
        return !matchingKeys.isEmpty();
    }

    @Override
    public Set<String> listSessionScopes(RuntimeContextScope context) {
        migrateLegacySession(context);
        String prefix = keyStrategy.scopePrefix(context);
        return entries.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .map(key -> key.substring(prefix.length()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Optional<AgentStateEntry> migrateLegacyEntry(RuntimeContextScope context, String scope) {
        String legacyKey = keyStrategy.legacyKey(context, scope);
        AgentStateEntry legacy = entries.get(legacyKey);
        if (legacy == null) {
            return Optional.empty();
        }
        AgentStateEntry migrated = migratedEntry(context, scope, legacy);
        entries.put(migrated.key(), migrated);
        entries.remove(legacyKey);
        return Optional.of(migrated);
    }

    private void migrateLegacySession(RuntimeContextScope context) {
        String legacyPrefix = keyStrategy.legacyScopePrefix(context);
        entries.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(legacyPrefix))
                .map(entry -> Map.entry(entry.getKey().substring(legacyPrefix.length()), entry.getValue()))
                .toList()
                .forEach(entry -> {
                    AgentStateEntry migrated = migratedEntry(context, entry.getKey(), entry.getValue());
                    entries.putIfAbsent(migrated.key(), migrated);
                    entries.remove(entry.getValue().key());
                });
    }

    private AgentStateEntry migratedEntry(RuntimeContextScope context, String scope, AgentStateEntry legacy) {
        String normalizedScope = keyStrategy.normalizeScope(scope);
        return new AgentStateEntry(
                keyStrategy.key(context, normalizedScope),
                context.ownerScopeId(),
                context.ownerId(),
                context.agentId(),
                context.sessionId(),
                normalizedScope,
                legacy.value(),
                legacy.updatedAt());
    }

    private void removeLegacyIfDifferent(String ownerKey, RuntimeContextScope context, String scope) {
        String legacyKey = keyStrategy.legacyKey(context, scope);
        if (!ownerKey.equals(legacyKey)) {
            entries.remove(legacyKey);
        }
    }
}
