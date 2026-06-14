package com.harnessagent.security;

import java.util.Set;
import java.util.stream.Collectors;

public record IdentityAssertion(
        String tenantId,
        String userId,
        IdentityProviderType providerType,
        Set<String> roles,
        Set<String> departments,
        boolean authenticated) {

    public IdentityAssertion {
        tenantId = require(tenantId, "tenantId");
        userId = require(userId, "userId");
        providerType = providerType == null ? IdentityProviderType.INTERNAL : providerType;
        roles = safeSet(roles);
        departments = safeSet(departments);
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
