package com.harnessagent.production.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.production.state.AgentStateEntry;
import com.harnessagent.production.state.AgentStateStore;
import com.harnessagent.production.state.StateStorePlan;
import com.harnessagent.production.state.TenantStateKeyStrategy;
import com.harnessagent.runtime.RuntimeContextScope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class LocalJsonAgentStateStore implements AgentStateStore {

    private final TenantStateKeyStrategy keyStrategy;
    private final StateStorePlan plan;
    private final Path rootDirectory;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public LocalJsonAgentStateStore(TenantStateKeyStrategy keyStrategy, StateStorePlan plan) {
        this.keyStrategy = keyStrategy;
        this.plan = plan;
        this.rootDirectory = Path.of(plan.location() == null || plan.location().isBlank()
                ? ".harness-agent/sessions"
                : plan.location()).toAbsolutePath().normalize();
    }

    @Override
    public StateStorePlan plan() {
        return plan;
    }

    @Override
    public AgentStateEntry save(RuntimeContextScope context, String scope, String value) {
        AgentStateEntry entry = new AgentStateEntry(
                keyStrategy.key(context, scope),
                context.tenantId(),
                context.userId(),
                context.agentId(),
                context.sessionId(),
                scope,
                value,
                Instant.now());
        write(entry);
        return entry;
    }

    @Override
    public Optional<AgentStateEntry> load(RuntimeContextScope context, String scope) {
        Path file = fileForKey(keyStrategy.key(context, scope));
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(read(file));
    }

    @Override
    public boolean delete(RuntimeContextScope context, String scope) {
        try {
            return Files.deleteIfExists(fileForKey(keyStrategy.key(context, scope)));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to delete local AgentState: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean exists(RuntimeContextScope context, String sessionScope) {
        String prefix = scopePrefix(context, sessionScopePrefix(sessionScope));
        return entries().stream().anyMatch(entry -> entry.key().startsWith(prefix));
    }

    @Override
    public boolean deleteSession(RuntimeContextScope context, String sessionScope) {
        String prefix = scopePrefix(context, sessionScopePrefix(sessionScope));
        Set<String> keys = entries().stream()
                .filter(entry -> entry.key().startsWith(prefix))
                .map(AgentStateEntry::key)
                .collect(Collectors.toSet());
        keys.forEach(key -> {
            try {
                Files.deleteIfExists(fileForKey(key));
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to delete local AgentState: " + ex.getMessage(), ex);
            }
        });
        return !keys.isEmpty();
    }

    @Override
    public Set<String> listSessionScopes(RuntimeContextScope context) {
        String prefix = scopePrefix(context, "");
        return entries().stream()
                .filter(entry -> entry.key().startsWith(prefix))
                .map(entry -> entry.key().substring(prefix.length()))
                .collect(Collectors.toUnmodifiableSet());
    }

    private void write(AgentStateEntry entry) {
        try {
            Files.createDirectories(rootDirectory);
            objectMapper.writeValue(fileForKey(entry.key()).toFile(), entry);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save local AgentState: " + ex.getMessage(), ex);
        }
    }

    private AgentStateEntry read(Path file) {
        try {
            return objectMapper.readValue(file.toFile(), AgentStateEntry.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load local AgentState: " + ex.getMessage(), ex);
        }
    }

    private List<AgentStateEntry> entries() {
        if (!Files.isDirectory(rootDirectory)) {
            return List.of();
        }
        try (var files = Files.list(rootDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::read)
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to list local AgentState: " + ex.getMessage(), ex);
        }
    }

    private Path fileForKey(String key) {
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(key.getBytes(StandardCharsets.UTF_8));
        return rootDirectory.resolve(encoded + ".json");
    }

    private static String scopePrefix(RuntimeContextScope context, String scope) {
        return String.join(":",
                "tenant", context.tenantId(),
                "user", context.userId(),
                "agent", context.agentId(),
                "session", context.sessionId(),
                "scope", scope);
    }

    private static String sessionScopePrefix(String sessionScope) {
        String scope = sessionScope == null || sessionScope.isBlank() ? "default" : sessionScope.trim();
        return scope.endsWith(":") ? scope : scope + ":";
    }
}
