package com.harnessagent.production.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.production.state.AgentStateEntry;
import com.harnessagent.production.state.AgentStateStore;
import com.harnessagent.production.state.OwnerStateKeyStrategy;
import com.harnessagent.production.state.StateStorePlan;
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

    private final OwnerStateKeyStrategy keyStrategy;
    private final StateStorePlan plan;
    private final Path rootDirectory;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public LocalJsonAgentStateStore(OwnerStateKeyStrategy keyStrategy, StateStorePlan plan) {
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
                context.ownerScopeId(),
                context.ownerId(),
                context.agentId(),
                context.sessionId(),
                keyStrategy.normalizeScope(scope),
                value,
                Instant.now());
        write(entry);
        deleteLegacyIfDifferent(entry.key(), context, scope);
        return entry;
    }

    @Override
    public Optional<AgentStateEntry> load(RuntimeContextScope context, String scope) {
        Path file = fileForKey(keyStrategy.key(context, scope));
        if (Files.isRegularFile(file)) {
            return Optional.of(read(file));
        }
        Path legacyFile = fileForKey(keyStrategy.legacyKey(context, scope));
        if (!Files.isRegularFile(legacyFile)) {
            return Optional.empty();
        }
        AgentStateEntry migrated = migratedEntry(context, keyStrategy.normalizeScope(scope), read(legacyFile));
        write(migrated);
        deleteFileIfExists(legacyFile);
        return Optional.of(migrated);
    }

    @Override
    public boolean delete(RuntimeContextScope context, String scope) {
        boolean deletedOwner = deleteFileIfExists(fileForKey(keyStrategy.key(context, scope)));
        boolean deletedLegacy = deleteFileIfExists(fileForKey(keyStrategy.legacyKey(context, scope)));
        return deletedOwner || deletedLegacy;
    }

    @Override
    public boolean exists(RuntimeContextScope context, String sessionScope) {
        migrateLegacySession(context);
        String prefix = keyStrategy.sessionScopePrefix(context, sessionScope);
        return entries().stream().anyMatch(entry -> entry.key().startsWith(prefix));
    }

    @Override
    public boolean deleteSession(RuntimeContextScope context, String sessionScope) {
        migrateLegacySession(context);
        String prefix = keyStrategy.sessionScopePrefix(context, sessionScope);
        Set<String> keys = entries().stream()
                .filter(entry -> entry.key().startsWith(prefix))
                .map(AgentStateEntry::key)
                .collect(Collectors.toSet());
        keys.forEach(key -> deleteFileIfExists(fileForKey(key)));
        return !keys.isEmpty();
    }

    @Override
    public Set<String> listSessionScopes(RuntimeContextScope context) {
        migrateLegacySession(context);
        String prefix = keyStrategy.scopePrefix(context);
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

    private void migrateLegacySession(RuntimeContextScope context) {
        String legacyPrefix = keyStrategy.legacyScopePrefix(context);
        entries().stream()
                .filter(entry -> entry.key().startsWith(legacyPrefix))
                .toList()
                .forEach(legacy -> {
                    String scope = legacy.key().substring(legacyPrefix.length());
                    AgentStateEntry migrated = migratedEntry(context, scope, legacy);
                    if (!Files.isRegularFile(fileForKey(migrated.key()))) {
                        write(migrated);
                    }
                    deleteFileIfExists(fileForKey(legacy.key()));
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

    private boolean deleteFileIfExists(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to delete local AgentState: " + ex.getMessage(), ex);
        }
    }

    private void deleteLegacyIfDifferent(String ownerKey, RuntimeContextScope context, String scope) {
        String legacyKey = keyStrategy.legacyKey(context, scope);
        if (!ownerKey.equals(legacyKey)) {
            deleteFileIfExists(fileForKey(legacyKey));
        }
    }

    private Path fileForKey(String key) {
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(key.getBytes(StandardCharsets.UTF_8));
        return rootDirectory.resolve(encoded + ".json");
    }
}
