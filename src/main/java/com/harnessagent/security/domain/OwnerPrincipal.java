package com.harnessagent.security.domain;

import com.harnessagent.runtime.PersonalRuntimeDefaults;
import java.util.Set;

public class OwnerPrincipal {

    private final String scopeId;
    private final String ownerId;
    private final IdentityProviderType providerType;

    public OwnerPrincipal(OwnerIdentity ownerIdentity) {
        this(
                PersonalRuntimeDefaults.PERSONAL_SCOPE_ID,
                ownerIdentity.ownerId(),
                ownerIdentity.providerType());
    }

    public OwnerPrincipal(String ownerId, IdentityProviderType providerType) {
        this(PersonalRuntimeDefaults.PERSONAL_SCOPE_ID, ownerId, providerType);
    }

    public OwnerPrincipal(
            String scopeId,
            String ownerId,
            IdentityProviderType providerType) {
        this.scopeId = require(scopeId, "scopeId");
        this.ownerId = require(ownerId, "ownerId");
        this.providerType = providerType == null ? IdentityProviderType.INTERNAL : providerType;
    }

    public OwnerPrincipal(
            String scopeId,
            String ownerId,
            IdentityProviderType providerType,
            Set<String> ignoredOwnerHints,
            Set<String> ignoredGroupHints) {
        this(scopeId, ownerId, providerType);
    }

    public String scopeId() {
        return scopeId;
    }

    public String ownerScopeId() {
        return scopeId;
    }

    public String ownerId() {
        return ownerId;
    }

    public IdentityProviderType providerType() {
        return providerType;
    }

    public OwnerIdentity ownerIdentity() {
        return new OwnerIdentity(ownerId, providerType, true);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

}
