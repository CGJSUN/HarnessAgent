package com.harnessagent.orchestration.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record ExpertAgentDefinition(
        String id,
        String tenantId,
        String name,
        String purpose,
        String inputContract,
        String outputContract,
        Set<String> requiredRoles,
        Set<String> allowedTools,
        Set<String> allowedKnowledgeSources,
        String ownerId,
        boolean approved,
        boolean enabled,
        String version,
        Instant updatedAt) {

    public ExpertAgentDefinition {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        tenantId = require(tenantId, "tenantId");
        name = require(name, "name");
        purpose = require(purpose, "purpose");
        inputContract = inputContract == null ? "" : inputContract.trim();
        outputContract = outputContract == null ? "" : outputContract.trim();
        requiredRoles = safeSet(requiredRoles);
        allowedTools = safeSet(allowedTools);
        allowedKnowledgeSources = safeSet(allowedKnowledgeSources);
        ownerId = require(ownerId, "ownerId");
        version = version == null || version.isBlank() ? "v1" : version.trim();
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
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
