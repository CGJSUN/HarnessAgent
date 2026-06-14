package com.harnessagent.security;

import java.util.Set;
import java.util.stream.Collectors;

public record SecurityPrincipal(
        String tenantId,
        String userId,
        IdentityProviderType providerType,
        Set<String> roles,
        Set<String> departments) {

    public SecurityPrincipal {
        tenantId = require(tenantId, "tenantId");
        userId = require(userId, "userId");
        providerType = providerType == null ? IdentityProviderType.INTERNAL : providerType;
        roles = safeSet(roles);
        departments = safeSet(departments);
    }

    public boolean hasRole(String role) {
        return role != null && roles.contains(role);
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
