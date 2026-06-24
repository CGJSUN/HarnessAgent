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
import com.harnessagent.security.domain.OwnerPrincipal;
import com.harnessagent.security.persistence.InMemorySecurityActivityStore;
import com.harnessagent.security.persistence.SecurityActivityRecord;
import com.harnessagent.security.persistence.SecurityActivityStore;

@Service
public class SecurityActivityService {

    private final SensitiveDataRedactor redactor;
    private final AuthorizationService authorizationService;
    private final SecurityActivityStore store;
    private Duration retention = Duration.ofDays(365);

    @Autowired
    public SecurityActivityService(
            SensitiveDataRedactor redactor,
            AuthorizationService authorizationService,
            SecurityActivityStore store) {
        this.redactor = redactor;
        this.authorizationService = authorizationService;
        this.store = store;
    }

    public SecurityActivityService(
            SensitiveDataRedactor redactor,
            AuthorizationService authorizationService) {
        this(redactor, authorizationService, new InMemorySecurityActivityStore());
    }

    public SecurityActivityRecord record(
            OwnerPrincipal principal,
            ResourceType resourceType,
            String resourceId,
            String action,
            Map<String, Object> details) {
        SecurityActivityRecord record = new SecurityActivityRecord(
                null,
                Instant.now(),
                principal.scopeId(),
                principal.ownerId(),
                resourceType,
                resourceId,
                action,
                redactor.redactMap(details));
        return store.save(record);
    }

    public List<SecurityActivityRecord> search(
            OwnerPrincipal principal,
            String ownerScopeId,
            ResourceAccessPolicy activitySearchPolicy) {
        authorizationService.require(principal, activitySearchPolicy, Permission.SEARCH_ACTIVITY);
        Instant cutoff = Instant.now().minus(retention);
        return store.search(ownerScopeId, cutoff);
    }

    public void setRetention(Duration retention) {
        this.retention = retention == null ? Duration.ofDays(365) : retention;
    }
}
