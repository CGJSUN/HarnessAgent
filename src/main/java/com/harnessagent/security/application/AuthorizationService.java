package com.harnessagent.security.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.harnessagent.security.domain.Permission;
import com.harnessagent.security.domain.ResourceAccessPolicy;
import com.harnessagent.security.domain.SecurityDecision;
import com.harnessagent.security.domain.OwnerPrincipal;

@Service
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    public SecurityDecision check(
            OwnerPrincipal principal,
            ResourceAccessPolicy policy,
            Permission permission) {
        if (principal == null) {
            return SecurityDecision.deny("principal is required");
        }
        if (policy == null) {
            return SecurityDecision.deny("resource policy is required");
        }
        if (!principal.scopeId().equals(policy.ownerScopeId())) {
            return SecurityDecision.deny("owner scope access is denied");
        }
        if (!policy.permissions().contains(permission)) {
            return SecurityDecision.deny("permission is not granted");
        }
        if (policy.allowedOwnerIds().contains(principal.ownerId())) {
            return SecurityDecision.allow();
        }
        return SecurityDecision.deny("owner policy denied access");
    }

    public void require(
            OwnerPrincipal principal,
            ResourceAccessPolicy policy,
            Permission permission) {
        SecurityDecision decision = check(principal, policy, permission);
        if (!decision.allowed()) {
            log.warn(
                    "security authorization rejected ownerHash={} permission={} reason={}",
                    principal == null ? "empty" : SafeLogFields.owner(principal.ownerId()),
                    permission,
                    SafeLogFields.reasonCode(decision.reason()));
            throw new IllegalStateException(decision.reason());
        }
    }

}
