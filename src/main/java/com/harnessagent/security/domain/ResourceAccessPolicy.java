package com.harnessagent.security.domain;

import java.util.Set;
import java.util.stream.Collectors;

public record ResourceAccessPolicy(
        ResourceType resourceType,
        String tenantId,
        Set<String> allowedUsers,
        Set<String> allowedRoles,
        Set<String> allowedDepartments,
        Set<Permission> permissions) {

    public ResourceAccessPolicy {
        resourceType = resourceType == null ? ResourceType.AGENT : resourceType;
        tenantId = require(tenantId, "tenantId");
        allowedUsers = safeSet(allowedUsers);
        allowedRoles = safeSet(allowedRoles);
        allowedDepartments = safeSet(allowedDepartments);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public static ResourceAccessPolicy adminOnly(String tenantId, ResourceType resourceType, Permission permission) {
        return new ResourceAccessPolicy(
                resourceType,
                tenantId,
                Set.of(),
                Set.of("admin"),
                Set.of(),
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
