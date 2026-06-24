package com.harnessagent.tooling.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolPermissionPolicy(
        @JsonAlias("allowedUserIds") Set<String> allowedOwnerIds,
        Set<String> allowedAgentIds,
        Set<String> deniedOwnerIds) {

    public ToolPermissionPolicy {
        allowedOwnerIds = safeSet(allowedOwnerIds);
        allowedAgentIds = safeSet(allowedAgentIds);
        deniedOwnerIds = safeSet(deniedOwnerIds);
    }

    public static ToolPermissionPolicy allowAll() {
        return new ToolPermissionPolicy(Set.of(), Set.of(), Set.of());
    }

    public boolean permits(ToolPrincipal principal) {
        return !deniedOwnerIds.contains(principal.ownerId())
                && allowedOrContains(allowedOwnerIds, principal.ownerId())
                && allowedOrContains(allowedAgentIds, principal.agentId());
    }

    private static boolean allowedOrContains(Set<String> configured, String value) {
        return configured.isEmpty() || configured.contains(value);
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
