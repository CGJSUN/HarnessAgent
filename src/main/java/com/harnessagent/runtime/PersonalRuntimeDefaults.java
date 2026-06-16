package com.harnessagent.runtime;

public final class PersonalRuntimeDefaults {

    public static final String TENANT_ID = "personal";
    public static final String OWNER_ID = "personal-user";

    private PersonalRuntimeDefaults() {
    }

    public static String tenantId(String tenantId) {
        return isBlank(tenantId) ? TENANT_ID : tenantId.trim();
    }

    public static String ownerId(String tenantId, String userId) {
        if (isBlank(tenantId) && isBlank(userId)) {
            return OWNER_ID;
        }
        return userId;
    }

    public static boolean isPersonalRequest(String tenantId) {
        return isBlank(tenantId) || TENANT_ID.equals(tenantId.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
