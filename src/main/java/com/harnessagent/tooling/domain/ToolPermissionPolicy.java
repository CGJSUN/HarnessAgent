package com.harnessagent.tooling.domain;

import java.util.Set;
import java.util.stream.Collectors;

public record ToolPermissionPolicy(
        Set<String> allowedTenantIds,
        Set<String> allowedUserIds,
        Set<String> allowedAgentIds,
        Set<String> allowedDepartments,
        Set<String> allowedRoles) {

    public ToolPermissionPolicy {
        allowedTenantIds = safeSet(allowedTenantIds);
        allowedUserIds = safeSet(allowedUserIds);
        allowedAgentIds = safeSet(allowedAgentIds);
        allowedDepartments = safeSet(allowedDepartments);
        allowedRoles = safeSet(allowedRoles);
    }

    public static ToolPermissionPolicy allowAll() {
        return new ToolPermissionPolicy(Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    public boolean permits(ToolPrincipal principal) {
        return allowedOrContains(allowedTenantIds, principal.tenantId())
                && allowedOrContains(allowedUserIds, principal.userId())
                && allowedOrContains(allowedAgentIds, principal.agentId())
                && allowedOrIntersects(allowedDepartments, principal.departments())
                && allowedOrIntersects(allowedRoles, principal.roles());
    }

    private static boolean allowedOrContains(Set<String> configured, String value) {
        return configured.isEmpty() || configured.contains(value);
    }

    private static boolean allowedOrIntersects(Set<String> configured, Set<String> values) {
        return configured.isEmpty() || values.stream().anyMatch(configured::contains);
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
