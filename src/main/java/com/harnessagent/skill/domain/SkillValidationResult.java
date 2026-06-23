package com.harnessagent.skill.domain;

import java.util.List;

public record SkillValidationResult(
        String skillName,
        String version,
        String source,
        boolean valid,
        List<String> errors) {

    public SkillValidationResult {
        skillName = skillName == null ? "" : skillName.trim();
        version = version == null ? "" : version.trim();
        source = source == null ? "" : source.trim();
        errors = errors == null ? List.of() : List.copyOf(errors);
        valid = valid && errors.isEmpty();
    }
}
