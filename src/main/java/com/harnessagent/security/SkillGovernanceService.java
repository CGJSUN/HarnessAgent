package com.harnessagent.security;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SkillGovernanceService {

    private final Map<String, SkillVersion> versions = new ConcurrentHashMap<>();

    public SkillVersion propose(
            String tenantId,
            String skillName,
            String version,
            String repository,
            String ownerId) {
        SkillVersion skill = new SkillVersion(
                null,
                tenantId,
                skillName,
                version,
                repository,
                ownerId,
                SkillStatus.PROPOSED,
                "",
                Instant.now());
        versions.put(skill.id(), skill);
        return skill;
    }

    public SkillVersion approve(String versionId, String reviewerId) {
        SkillVersion current = requireVersion(versionId);
        return save(current.withStatus(SkillStatus.APPROVED, reviewerId));
    }

    public SkillVersion publish(String versionId) {
        SkillVersion current = requireVersion(versionId);
        if (current.status() != SkillStatus.APPROVED) {
            throw new IllegalStateException("Skill version must be approved before publish");
        }
        return save(current.withStatus(SkillStatus.PUBLISHED, current.approvedBy()));
    }

    public SkillVersion disable(String versionId) {
        return save(requireVersion(versionId).withStatus(SkillStatus.DISABLED, null));
    }

    public SkillVersion rollback(String activeVersionId, String targetVersionId) {
        SkillVersion active = requireVersion(activeVersionId);
        SkillVersion target = requireVersion(targetVersionId);
        if (!active.skillName().equals(target.skillName()) || !active.tenantId().equals(target.tenantId())) {
            throw new IllegalArgumentException("Rollback target must belong to the same skill and tenant");
        }
        save(active.withStatus(SkillStatus.ROLLED_BACK, null));
        return save(target.withStatus(SkillStatus.PUBLISHED, target.approvedBy()));
    }

    public List<SkillVersion> list(String tenantId, String skillName) {
        return versions.values().stream()
                .filter(version -> version.tenantId().equals(tenantId))
                .filter(version -> skillName == null || skillName.isBlank() || version.skillName().equals(skillName))
                .sorted(Comparator.comparing(SkillVersion::updatedAt))
                .toList();
    }

    private SkillVersion save(SkillVersion version) {
        versions.put(version.id(), version);
        return version;
    }

    private SkillVersion requireVersion(String versionId) {
        SkillVersion version = versions.get(versionId);
        if (version == null) {
            throw new IllegalArgumentException("Unknown skill version: " + versionId);
        }
        return version;
    }
}
