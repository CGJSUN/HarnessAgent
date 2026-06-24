package com.harnessagent.api.request;

public record SkillRepositoryRefreshRequest(
        String ownerId,
        String agentId,
        String repositoryRoot) {
}
