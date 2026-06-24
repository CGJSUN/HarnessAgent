package com.harnessagent.orchestration.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record ExpertAgentDefinition(
        String id,
        String ownerScopeId,
        String name,
        String purpose,
        String inputContract,
        String outputContract,
        Set<String> allowedOwnerIds,
        Set<String> allowedTools,
        Set<String> allowedSkills,
        Set<String> allowedKnowledgeSources,
        ContextBoundary contextBoundary,
        String ownerId,
        boolean approved,
        boolean enabled,
        String version,
        Instant updatedAt) {

    public ExpertAgentDefinition {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        ownerScopeId = require(ownerScopeId, "ownerScopeId");
        name = require(name, "name");
        purpose = require(purpose, "purpose");
        inputContract = inputContract == null ? "" : inputContract.trim();
        outputContract = outputContract == null ? "" : outputContract.trim();
        allowedOwnerIds = safeSet(allowedOwnerIds);
        allowedTools = safeSet(allowedTools);
        allowedSkills = safeSet(allowedSkills);
        allowedKnowledgeSources = safeSet(allowedKnowledgeSources);
        contextBoundary = contextBoundary == null
                ? new ContextBoundary(false, false, false, true, Set.of("question", "citations"))
                : contextBoundary;
        ownerId = require(ownerId, "ownerId");
        version = version == null || version.isBlank() ? "v1" : version.trim();
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    public ExpertAgentDefinition(
            String id,
            String ownerScopeId,
            String name,
            String purpose,
            String inputContract,
            String outputContract,
            Set<String> allowedOwnerIds,
            Set<String> allowedTools,
            Set<String> allowedKnowledgeSources,
            String ownerId,
            boolean approved,
            boolean enabled,
            String version,
            Instant updatedAt) {
        this(
                id,
                ownerScopeId,
                name,
                purpose,
                inputContract,
                outputContract,
                allowedOwnerIds,
                allowedTools,
                Set.of(),
                allowedKnowledgeSources,
                new ContextBoundary(false, false, false, true, Set.of("question", "citations")),
                ownerId,
                approved,
                enabled,
                version,
                updatedAt);
    }

    public boolean canHandle(String taskIntent) {
        if (taskIntent == null || taskIntent.isBlank()) {
            return false;
        }
        String normalizedIntent = taskIntent.toLowerCase(java.util.Locale.ROOT);
        return name.toLowerCase(java.util.Locale.ROOT).contains(normalizedIntent)
                || purpose.toLowerCase(java.util.Locale.ROOT).contains(normalizedIntent)
                || normalizedIntent.contains(name.toLowerCase(java.util.Locale.ROOT));
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static Set<String> safeSet(Set<String> input) {
        if (input == null) {
            return Set.of();
        }
        return input.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }
}
