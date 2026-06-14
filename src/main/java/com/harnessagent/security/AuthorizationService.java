package com.harnessagent.security;

import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

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
