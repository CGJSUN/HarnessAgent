package com.harnessagent.security.domain;

import java.util.Set;
import java.util.stream.Collectors;

public record ResourceAccessPolicy(
        ResourceType resourceType,
        String ownerScopeId,
        Set<String> allowedOwnerIds,
        Set<Permission> permissions) {

    public ResourceAccessPolicy {
        resourceType = resourceType == null ? ResourceType.AGENT : resourceType;
        ownerScopeId = require(ownerScopeId, "ownerScopeId");
        allowedOwnerIds = safeSet(allowedOwnerIds);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public static ResourceAccessPolicy ownerOnly(
            String ownerScopeId,
            String ownerId,
            ResourceType resourceType,
            Permission permission) {
        return new ResourceAccessPolicy(
                resourceType,
                ownerScopeId,
                Set.of(ownerId),
                Set.of(permission));
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
