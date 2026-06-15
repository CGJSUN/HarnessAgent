package com.harnessagent.production;

import com.harnessagent.runtime.RuntimeContextScope;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class AgentScopeStateStoreAdapter implements io.agentscope.core.state.AgentStateStore {

    private final RuntimeContextScope context;
    private final AgentStateStore store;

    public AgentScopeStateStoreAdapter(RuntimeContextScope context, AgentStateStore store) {
        this.context = context;
        this.store = store;
    }

    @Override
    public void save(String userId, String sessionId, String key, State value) {
        store.save(context, scope(userId, sessionId, key), JsonUtils.getJsonCodec().toJson(value));
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> values) {
        String jsonLines = values.stream()
                .map(value -> JsonUtils.getJsonCodec().toJson(value))
                .collect(Collectors.joining("\n"));
        store.save(context, scope(userId, sessionId, key), jsonLines);
    }

    @Override
    public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
        return store.load(context, scope(userId, sessionId, key))
                .filter(entry -> !entry.value().isBlank())
                .map(entry -> JsonUtils.getJsonCodec().fromJson(entry.value(), type));
    }

    @Override
    public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> itemType) {
        return store.load(context, scope(userId, sessionId, key))
                .map(AgentStateEntry::value)
                .filter(value -> !value.isBlank())
                .map(value -> Arrays.stream(value.split("\\R"))
                        .filter(line -> !line.isBlank())
                        .map(line -> JsonUtils.getJsonCodec().fromJson(line, itemType))
                        .toList())
                .orElseGet(List::of);
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        return store.exists(context, sessionScope(userId, sessionId));
    }

    @Override
    public void delete(String userId, String sessionId) {
        store.deleteSession(context, sessionScope(userId, sessionId));
    }

    @Override
    public void delete(String userId, String sessionId, String key) {
        store.delete(context, scope(userId, sessionId, key));
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        String prefix = userScope(userId);
        return store.listSessionScopes(context).stream()
                .filter(scope -> scope.startsWith(prefix))
                .map(scope -> scope.substring(prefix.length()))
                .map(AgentScopeStateStoreAdapter::sessionIdFromScope)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String scope(String userId, String sessionId, String key) {
        String stateKey = key == null || key.isBlank() ? "default" : key.trim();
        return sessionScope(userId, sessionId) + stateKey;
    }

    private static String sessionScope(String userId, String sessionId) {
        return "agentscope:" + normalize(userId) + ":" + normalize(sessionId) + ":";
    }

    private static String userScope(String userId) {
        return "agentscope:" + normalize(userId) + ":";
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "default" : value.trim();
    }

    private static String sessionIdFromScope(String scopeSuffix) {
        int separator = scopeSuffix.indexOf(':');
        return separator < 0 ? scopeSuffix : scopeSuffix.substring(0, separator);
    }
}
