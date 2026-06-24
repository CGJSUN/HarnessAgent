package com.harnessagent.production.infrastructure;

import com.harnessagent.runtime.RuntimeContextScope;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.harnessagent.production.state.AgentStateEntry;
import com.harnessagent.production.state.AgentStateStore;

public class AgentScopeStateStoreAdapter implements io.agentscope.core.state.AgentStateStore {

    private final RuntimeContextScope context;
    private final AgentStateStore store;

    public AgentScopeStateStoreAdapter(RuntimeContextScope context, AgentStateStore store) {
        this.context = context;
        this.store = store;
    }

    @Override
    public void save(String ownerId, String sessionId, String key, State value) {
        store.save(context, scope(ownerId, sessionId, key), JsonUtils.getJsonCodec().toJson(value));
    }

    @Override
    public void save(String ownerId, String sessionId, String key, List<? extends State> values) {
        String jsonLines = values.stream()
                .map(value -> JsonUtils.getJsonCodec().toJson(value))
                .collect(Collectors.joining("\n"));
        store.save(context, scope(ownerId, sessionId, key), jsonLines);
    }

    @Override
    public <T extends State> Optional<T> get(String ownerId, String sessionId, String key, Class<T> type) {
        return load(ownerId, sessionId, key)
                .filter(entry -> !entry.value().isBlank())
                .map(entry -> JsonUtils.getJsonCodec().fromJson(entry.value(), type));
    }

    @Override
    public <T extends State> List<T> getList(String ownerId, String sessionId, String key, Class<T> itemType) {
        return load(ownerId, sessionId, key)
                .map(AgentStateEntry::value)
                .filter(value -> !value.isBlank())
                .map(value -> Arrays.stream(value.split("\\R"))
                        .filter(line -> !line.isBlank())
                        .map(line -> JsonUtils.getJsonCodec().fromJson(line, itemType))
                        .toList())
                .orElseGet(List::of);
    }

    @Override
    public boolean exists(String ownerId, String sessionId) {
        migrateLegacyRuntimeUserScopes(ownerId);
        return store.exists(context, sessionScope(ownerId, sessionId));
    }

    @Override
    public void delete(String ownerId, String sessionId) {
        store.deleteSession(context, sessionScope(ownerId, sessionId));
        legacySessionScope(ownerId, sessionId).ifPresent(scope -> store.deleteSession(context, scope));
    }

    @Override
    public void delete(String ownerId, String sessionId, String key) {
        store.delete(context, scope(ownerId, sessionId, key));
        legacyScope(ownerId, sessionId, key).ifPresent(scope -> store.delete(context, scope));
    }

    @Override
    public Set<String> listSessionIds(String ownerId) {
        migrateLegacyRuntimeUserScopes(ownerId);
        String prefix = userScope(ownerId);
        return store.listSessionScopes(context).stream()
                .filter(scope -> scope.startsWith(prefix))
                .map(scope -> scope.substring(prefix.length()))
                .map(this::sessionIdFromScope)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Optional<AgentStateEntry> load(String ownerId, String sessionId, String key) {
        String currentScope = scope(ownerId, sessionId, key);
        Optional<AgentStateEntry> current = store.load(context, currentScope);
        if (current.isPresent()) {
            return current;
        }
        return legacyScope(ownerId, sessionId, key).flatMap(legacyScope -> store.load(context, legacyScope)
                .map(legacy -> {
                    AgentStateEntry migrated = store.save(context, currentScope, legacy.value());
                    store.delete(context, legacyScope);
                    return migrated;
                }));
    }

    private void migrateLegacyRuntimeUserScopes(String ownerId) {
        Optional<String> legacyUserScope = legacyUserScope(ownerId);
        if (legacyUserScope.isEmpty()) {
            return;
        }
        String legacyPrefix = legacyUserScope.get();
        String currentPrefix = userScope(ownerId);
        store.listSessionScopes(context).stream()
                .filter(scope -> scope.startsWith(legacyPrefix))
                .toList()
                .forEach(legacyScope -> {
                    String migratedScope = currentPrefix + legacyScope.substring(legacyPrefix.length());
                    if (store.load(context, migratedScope).isEmpty()) {
                        store.load(context, legacyScope)
                                .ifPresent(legacy -> store.save(context, migratedScope, legacy.value()));
                    }
                    store.delete(context, legacyScope);
                });
    }

    private static String scope(String ownerId, String sessionId, String key) {
        String stateKey = key == null || key.isBlank() ? "default" : key.trim();
        return sessionScope(ownerId, sessionId) + stateKey;
    }

    private static String sessionScope(String ownerId, String sessionId) {
        return "agentscope:" + normalize(ownerId) + ":" + normalize(sessionId) + ":";
    }

    private static String userScope(String ownerId) {
        return "agentscope:" + normalize(ownerId) + ":";
    }

    private Optional<String> legacyScope(String ownerId, String sessionId, String key) {
        return legacySessionScope(ownerId, sessionId)
                .map(scope -> scope + (key == null || key.isBlank() ? "default" : key.trim()));
    }

    private Optional<String> legacySessionScope(String ownerId, String sessionId) {
        return legacyUserScope(ownerId)
                .map(scope -> scope + normalize(sessionId) + ":");
    }

    private Optional<String> legacyUserScope(String ownerId) {
        String legacyRuntimeUserId = context.ownerScopeId() + ":" + context.ownerId();
        String normalizedUserId = normalize(ownerId);
        if (!normalizedUserId.equals(context.runtimeUserId()) || legacyRuntimeUserId.equals(normalizedUserId)) {
            return Optional.empty();
        }
        return Optional.of("agentscope:" + legacyRuntimeUserId + ":");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "default" : value.trim();
    }

    private String sessionIdFromScope(String scopeSuffix) {
        String currentSessionId = normalize(context.runtimeSessionId());
        if (scopeSuffix.startsWith(currentSessionId + ":")) {
            return currentSessionId;
        }
        int separator = scopeSuffix.lastIndexOf(':');
        return separator < 0 ? scopeSuffix : scopeSuffix.substring(0, separator);
    }
}
