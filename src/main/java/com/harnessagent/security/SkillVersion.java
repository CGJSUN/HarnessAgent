package com.harnessagent.security;

import java.time.Instant;
import java.util.UUID;

public record SkillVersion(
        String id,
        String tenantId,
        String skillName,
        String version,
        String repository,
        String ownerId,
        SkillStatus status,
        String approvedBy,
        Instant updatedAt) {

    public SkillVersion {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        tenantId = require(tenantId, "tenantId");
        skillName = require(skillName, "skillName");
        version = require(version, "version");
        repository = repository == null ? "" : repository.trim();
        ownerId = require(ownerId, "ownerId");
        status = status == null ? SkillStatus.PROPOSED : status;
        approvedBy = approvedBy == null ? "" : approvedBy.trim();
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    public SkillVersion withStatus(SkillStatus nextStatus, String reviewerId) {
        return new SkillVersion(
                id,
                tenantId,
                skillName,
                version,
                repository,
                ownerId,
                nextStatus,
                reviewerId == null ? approvedBy : reviewerId,
                Instant.now());
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
