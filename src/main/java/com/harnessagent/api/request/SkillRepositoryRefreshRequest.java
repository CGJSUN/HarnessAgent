package com.harnessagent.api.request;

import java.util.Set;

public record SkillRepositoryRefreshRequest(
        String tenantId,
        String userId,
        String agentId,
        Set<String> roles,
        Set<String> departments,
        String repositoryRoot) {
}
