package com.harnessagent.runtime;

public final class PersonalRuntimeDefaults {

    public static final String PERSONAL_SCOPE_ID = "personal";
    public static final String DEFAULT_OWNER_ID = "personal-user";

    private PersonalRuntimeDefaults() {
    }

    public static String ownerId(String ownerId) {
        return isBlank(ownerId) ? DEFAULT_OWNER_ID : ownerId.trim();
    }

    public static String ownerScopeId(String ownerScopeId) {
        return isBlank(ownerScopeId) ? PERSONAL_SCOPE_ID : ownerScopeId.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
