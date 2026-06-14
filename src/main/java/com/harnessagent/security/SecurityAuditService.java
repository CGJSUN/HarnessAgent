package com.harnessagent.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class SecurityAuditService {

    private final SensitiveDataRedactor redactor;
    private final AuthorizationService authorizationService;
    private final List<SecurityAuditRecord> records = new CopyOnWriteArrayList<>();
    private Duration retention = Duration.ofDays(365);

    public SecurityAuditService(
            SensitiveDataRedactor redactor,
            AuthorizationService authorizationService) {
        this.redactor = redactor;
        this.authorizationService = authorizationService;
    }

    public SecurityAuditRecord record(
            SecurityPrincipal principal,
            ResourceType resourceType,
            String resourceId,
            String action,
            Map<String, Object> details) {
        SecurityAuditRecord record = new SecurityAuditRecord(
                null,
                Instant.now(),
                principal.tenantId(),
                principal.userId(),
                resourceType,
                resourceId,
                action,
                redactor.redactMap(details));
        records.add(record);
        return record;
    }

    public List<SecurityAuditRecord> search(
            SecurityPrincipal principal,
            String tenantId,
            ResourceAccessPolicy auditSearchPolicy) {
        authorizationService.require(principal, auditSearchPolicy, Permission.SEARCH_AUDIT);
        Instant cutoff = Instant.now().minus(retention);
        return records.stream()
                .filter(record -> record.tenantId().equals(tenantId))
                .filter(record -> !record.occurredAt().isBefore(cutoff))
                .sorted(Comparator.comparing(SecurityAuditRecord::occurredAt))
                .toList();
    }

    public void setRetention(Duration retention) {
        this.retention = retention == null ? Duration.ofDays(365) : retention;
    }
}
