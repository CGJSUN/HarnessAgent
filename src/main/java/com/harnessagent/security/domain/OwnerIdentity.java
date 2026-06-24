package com.harnessagent.security.domain;

public record OwnerIdentity(String ownerId, IdentityProviderType providerType, boolean authenticated) {

    public OwnerIdentity {
        ownerId = require(ownerId, "ownerId");
        providerType = providerType == null ? IdentityProviderType.INTERNAL : providerType;
    }

    public static OwnerIdentity local(String ownerId) {
        return new OwnerIdentity(ownerId, IdentityProviderType.INTERNAL, false);
    }

    public static OwnerIdentity trusted(String ownerId, IdentityProviderType providerType) {
        return new OwnerIdentity(ownerId, providerType, true);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
