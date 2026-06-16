package com.harnessagent.security.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.harnessagent.security.domain.Permission;
import com.harnessagent.security.domain.ResourceAccessPolicy;
import com.harnessagent.security.domain.SecurityDecision;
import com.harnessagent.security.domain.SecurityPrincipal;

@Service
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    public SecurityDecision check(
            SecurityPrincipal principal,
            ResourceAccessPolicy policy,
            Permission permission) {
        if (principal == null) {
            return SecurityDecision.deny("principal is required");
        }
        if (policy == null) {
            return SecurityDecision.deny("resource policy is required");
        }
        if (!principal.tenantId().equals(policy.tenantId())) {
            return SecurityDecision.deny("cross-tenant access is denied");
        }
        if (!policy.permissions().contains(permission)) {
            return SecurityDecision.deny("permission is not granted");
        }
        if (principal.hasRole("admin")
                || policy.allowedUsers().contains(principal.userId())
                || intersects(policy.allowedRoles(), principal.roles())
                || intersects(policy.allowedDepartments(), principal.departments())) {
            return SecurityDecision.allow();
        }
        return SecurityDecision.deny("RBAC policy denied access");
    }

    public void require(
            SecurityPrincipal principal,
            ResourceAccessPolicy policy,
            Permission permission) {
        SecurityDecision decision = check(principal, policy, permission);
        if (!decision.allowed()) {
            log.warn(
                    "security authorization rejected tenantId={} userHash={} permission={} reason={}",
                    principal == null ? "" : principal.tenantId(),
                    principal == null ? "empty" : SafeLogFields.user(principal.userId()),
                    permission,
                    SafeLogFields.reasonCode(decision.reason()));
            throw new IllegalStateException(decision.reason());
        }
    }

    private static boolean intersects(java.util.Set<String> first, java.util.Set<String> second) {
        if (first.isEmpty() || second.isEmpty()) {
            return false;
        }
        return first.stream().anyMatch(second::contains);
    }
}
