package com.harnessagent.skill.domain;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

public record PersonalSkillMetadata(
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

    public PersonalSkillMetadata {
        tenantId = require(tenantId, "tenantId");
        ownerId = require(ownerId, "ownerId");
        name = require(name, "name");
        version = require(version, "version");
        id = id == null || id.isBlank() ? tenantId + ":" + name + ":" + version : id.trim();
        description = description == null ? "" : description.trim();
        triggers = safeSet(triggers);
        sourceType = sourceType == null ? SkillRepositoryType.LOCAL : sourceType;
        source = source == null ? "" : source.trim();
        permissions = permissions == null ? SkillPermissionSet.none() : permissions;
        resources = safeSet(resources);
        agentIds = safeSet(agentIds);
        status = status == null ? PersonalSkillStatus.ENABLED : status;
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    public PersonalSkillMetadata withStatus(PersonalSkillStatus nextStatus) {
        return new PersonalSkillMetadata(
                id,
                tenantId,
                ownerId,
                name,
                description,
                version,
                triggers,
                sourceType,
                source,
                permissions,
                resources,
                agentIds,
                nextStatus,
                Instant.now());
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
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
