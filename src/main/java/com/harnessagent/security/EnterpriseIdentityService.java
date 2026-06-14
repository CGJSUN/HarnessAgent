package com.harnessagent.security;

import org.springframework.stereotype.Service;

@Service
public class EnterpriseIdentityService {

    public SecurityPrincipal authenticate(IdentityAssertion assertion) {
        if (assertion == null) {
            throw new IllegalArgumentException("identity assertion is required");
        }
        if (!assertion.authenticated()) {
            throw new IllegalStateException("Enterprise identity is not authenticated");
        }
        return new SecurityPrincipal(
                assertion.tenantId(),
                assertion.userId(),
                assertion.providerType(),
                assertion.roles(),
                assertion.departments());
    }
}
