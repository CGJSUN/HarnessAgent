package com.harnessagent.api.request;

import java.util.Set;

public record SkillValidationRequest(
        String tenantId,
        String userId,
        String agentId,
        Set<String> roles,
        Set<String> departments,
        String skillDirectory) {
}
