package com.harnessagent.api.response;

import com.harnessagent.skill.domain.SkillValidationResult;
import java.util.List;

public record SkillValidationResponse(
        String skillName,
        String version,
        String source,
        boolean valid,
        List<String> errors) {

    public static SkillValidationResponse from(SkillValidationResult result) {
        return new SkillValidationResponse(
                result.skillName(),
                result.version(),
                result.source(),
                result.valid(),
                result.errors());
    }
}
