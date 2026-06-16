package com.harnessagent.security.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.harnessagent.security.domain.SkillStatus;
import com.harnessagent.security.domain.SkillVersion;

@Service
public class SkillGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(SkillGovernanceService.class);

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
        log.info("skill proposed tenantId={} skillName={} version={} ownerHash={}",
                tenantId, skillName, version, SafeLogFields.user(ownerId));
        return skill;
    }

    public SkillVersion approve(String versionId, String reviewerId) {
        SkillVersion current = requireVersion(versionId);
        SkillVersion approved = save(current.withStatus(SkillStatus.APPROVED, reviewerId));
        log.info("skill approved tenantId={} skillName={} version={} reviewerHash={}",
                approved.tenantId(), approved.skillName(), approved.version(), SafeLogFields.user(reviewerId));
        return approved;
    }

    public SkillVersion publish(String versionId) {
        SkillVersion current = requireVersion(versionId);
        if (current.status() != SkillStatus.APPROVED) {
            log.warn("skill publish rejected tenantId={} skillName={} version={} reason={}",
                    current.tenantId(), current.skillName(), current.version(), "not_approved");
            throw new IllegalStateException("Skill version must be approved before publish");
        }
        SkillVersion published = save(current.withStatus(SkillStatus.PUBLISHED, current.approvedBy()));
        log.info("skill published tenantId={} skillName={} version={}",
                published.tenantId(), published.skillName(), published.version());
        return published;
    }

    public SkillVersion disable(String versionId) {
        SkillVersion disabled = save(requireVersion(versionId).withStatus(SkillStatus.DISABLED, null));
        log.info("skill disabled tenantId={} skillName={} version={}",
                disabled.tenantId(), disabled.skillName(), disabled.version());
        return disabled;
    }

    public SkillVersion rollback(String activeVersionId, String targetVersionId) {
        SkillVersion active = requireVersion(activeVersionId);
        SkillVersion target = requireVersion(targetVersionId);
        if (!active.skillName().equals(target.skillName()) || !active.tenantId().equals(target.tenantId())) {
            log.warn("skill rollback rejected activeTenantId={} targetTenantId={} reason={}",
                    active.tenantId(), target.tenantId(), "target_mismatch");
            throw new IllegalArgumentException("Rollback target must belong to the same skill and tenant");
        }
        save(active.withStatus(SkillStatus.ROLLED_BACK, null));
        SkillVersion published = save(target.withStatus(SkillStatus.PUBLISHED, target.approvedBy()));
        log.info("skill rolled_back tenantId={} skillName={} activeVersion={} targetVersion={}",
                published.tenantId(), published.skillName(), active.version(), published.version());
        return published;
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
