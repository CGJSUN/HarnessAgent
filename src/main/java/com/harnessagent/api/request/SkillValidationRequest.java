package com.harnessagent.api.request;

public record SkillValidationRequest(
        String ownerId,
        String agentId,
        String skillDirectory) {
}
