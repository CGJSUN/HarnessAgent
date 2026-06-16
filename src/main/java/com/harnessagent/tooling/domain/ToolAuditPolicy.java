package com.harnessagent.tooling.domain;

import java.util.Set;
import java.util.stream.Collectors;

public record ToolAuditPolicy(
        boolean enabled,
        Set<String> sensitiveParameters,
        Set<String> sensitiveResultFields) {

    public ToolAuditPolicy {
        sensitiveParameters = safeSet(sensitiveParameters);
        sensitiveResultFields = safeSet(sensitiveResultFields);
    }

    public static ToolAuditPolicy enabled(Set<String> sensitiveParameters, Set<String> sensitiveResultFields) {
        return new ToolAuditPolicy(true, sensitiveParameters, sensitiveResultFields);
    }

    public static ToolAuditPolicy standard() {
        return new ToolAuditPolicy(true, Set.of(), Set.of());
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
