package com.harnessagent.security.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!production")
public class InMemorySecurityActivityStore implements SecurityActivityStore {

    private final List<SecurityActivityRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public SecurityActivityRecord save(SecurityActivityRecord record) {
        records.add(record);
        return record;
    }

    @Override
    public List<SecurityActivityRecord> search(String ownerScopeId, Instant occurredAtFromInclusive) {
        if (ownerScopeId == null || ownerScopeId.isBlank()) {
            throw new IllegalArgumentException("owner scope is required");
        }
        Instant cutoff = occurredAtFromInclusive == null ? Instant.EPOCH : occurredAtFromInclusive;
        return records.stream()
                .filter(record -> record.ownerScopeId().equals(ownerScopeId.trim()))
                .filter(record -> !record.occurredAt().isBefore(cutoff))
                .sorted(Comparator.comparing(SecurityActivityRecord::occurredAt))
                .toList();
    }
}
