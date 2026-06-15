package com.harnessagent.security;

import java.time.Instant;
import java.util.List;

public interface SecurityAuditStore {

    SecurityAuditRecord save(SecurityAuditRecord record);

    List<SecurityAuditRecord> search(String tenantId, Instant occurredAtFromInclusive);
}
