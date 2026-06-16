package com.harnessagent.security.application;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.harnessagent.security.domain.Permission;
import com.harnessagent.security.domain.ProtectedResource;
import com.harnessagent.security.domain.SecurityPrincipal;

@Service
public class DataPermissionService {

    private static final Logger log = LoggerFactory.getLogger(DataPermissionService.class);

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
        List<ProtectedResource> visible = resources.stream()
                .filter(resource -> authorizationService.check(principal, resource.policy(), permission).allowed())
                .toList();
        if (visible.size() < resources.size()) {
            log.debug(
                    "security permission filtered tenantId={} userHash={} permission={} candidateCount={} permittedCount={}",
                    principal.tenantId(),
                    SafeLogFields.user(principal.userId()),
                    permission,
                    resources.size(),
                    visible.size());
        }
        return visible;
    }
}
