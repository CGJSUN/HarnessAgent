package com.harnessagent.skill.domain;

import java.util.Map;

public record SkillExecutionResult(
        String skillName,
        String version,
        String injectedInstructions,
        Map<String, String> resources,
        Map<String, Object> context) {

    public SkillExecutionResult {
        skillName = skillName == null ? "" : skillName.trim();
        version = version == null ? "" : version.trim();
        injectedInstructions = injectedInstructions == null ? "" : injectedInstructions;
        resources = resources == null ? Map.of() : Map.copyOf(resources);
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
