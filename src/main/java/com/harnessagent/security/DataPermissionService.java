package com.harnessagent.security;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DataPermissionService {

    private final AuthorizationService authorizationService;

    public DataPermissionService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public List<ProtectedResource> filterVisible(
            SecurityPrincipal principal,
            List<ProtectedResource> resources,
            Permission permission) {
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        return resources.stream()
                .filter(resource -> authorizationService.check(principal, resource.policy(), permission).allowed())
                .toList();
    }
}
