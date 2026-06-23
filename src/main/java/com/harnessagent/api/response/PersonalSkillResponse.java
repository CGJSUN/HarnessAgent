package com.harnessagent.api.response;

import com.harnessagent.skill.domain.PersonalSkillMetadata;
import com.harnessagent.skill.domain.PersonalSkillStatus;
import com.harnessagent.skill.domain.SkillPermissionSet;
import com.harnessagent.skill.domain.SkillRepositoryType;
import java.time.Instant;
import java.util.Set;

public record PersonalSkillResponse(
        String id,
        String tenantId,
        String ownerId,
        String name,
        String description,
        String version,
        Set<String> triggers,
        SkillRepositoryType sourceType,
        String source,
        SkillPermissionSet permissions,
        Set<String> resources,
        Set<String> agentIds,
        PersonalSkillStatus status,
        Instant updatedAt) {

    public static PersonalSkillResponse from(PersonalSkillMetadata metadata) {
        return new PersonalSkillResponse(
                metadata.id(),
                metadata.tenantId(),
                metadata.ownerId(),
                metadata.name(),
                metadata.description(),
                metadata.version(),
                metadata.triggers(),
                metadata.sourceType(),
                metadata.source(),
                metadata.permissions(),
                metadata.resources(),
                metadata.agentIds(),
                metadata.status(),
                metadata.updatedAt());
    }
}
