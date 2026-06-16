package com.harnessagent.security.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.harnessagent.security.domain.Permission;
import com.harnessagent.security.domain.ResourceAccessPolicy;
import com.harnessagent.security.domain.ResourceType;
import com.harnessagent.security.domain.SecurityPrincipal;
import com.harnessagent.security.persistence.InMemorySecurityAuditStore;
import com.harnessagent.security.persistence.SecurityAuditRecord;
import com.harnessagent.security.persistence.SecurityAuditStore;

@Service
public class SecurityAuditService {

    private final SensitiveDataRedactor redactor;
    private final AuthorizationService authorizationService;
    private final SecurityAuditStore store;
    private Duration retention = Duration.ofDays(365);

    @Autowired
    public SecurityAuditService(
            SensitiveDataRedactor redactor,
            AuthorizationService authorizationService,
            SecurityAuditStore store) {
        this.redactor = redactor;
        this.authorizationService = authorizationService;
        this.store = store;
    }

    public SecurityAuditService(
            SensitiveDataRedactor redactor,
            AuthorizationService authorizationService) {
        this(redactor, authorizationService, new InMemorySecurityAuditStore());
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
        return store.save(record);
    }

    public List<SecurityAuditRecord> search(
            SecurityPrincipal principal,
            String tenantId,
            ResourceAccessPolicy auditSearchPolicy) {
        authorizationService.require(principal, auditSearchPolicy, Permission.SEARCH_AUDIT);
        Instant cutoff = Instant.now().minus(retention);
        return store.search(tenantId, cutoff);
    }

    public void setRetention(Duration retention) {
        this.retention = retention == null ? Duration.ofDays(365) : retention;
    }
}
