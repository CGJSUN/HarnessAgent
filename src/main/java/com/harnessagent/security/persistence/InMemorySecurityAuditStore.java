package com.harnessagent.security.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!production")
public class InMemorySecurityAuditStore implements SecurityAuditStore {

    private final List<SecurityAuditRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public SecurityAuditRecord save(SecurityAuditRecord record) {
        records.add(record);
        return record;
    }

    @Override
    public List<SecurityAuditRecord> search(String tenantId, Instant occurredAtFromInclusive) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        Instant cutoff = occurredAtFromInclusive == null ? Instant.EPOCH : occurredAtFromInclusive;
        return records.stream()
                .filter(record -> record.tenantId().equals(tenantId.trim()))
                .filter(record -> !record.occurredAt().isBefore(cutoff))
                .sorted(Comparator.comparing(SecurityAuditRecord::occurredAt))
                .toList();
    }
}
