package com.harnessagent.skill.domain;

import java.util.Set;
import java.util.stream.Collectors;

public record SkillPermissionSet(
        Set<String> files,
        Set<String> tools,
        boolean network,
        boolean sandbox,
        boolean memory) {

    public SkillPermissionSet {
        files = safeSet(files);
        tools = safeSet(tools);
    }

    public static SkillPermissionSet none() {
        return new SkillPermissionSet(Set.of(), Set.of(), false, false, false);
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
