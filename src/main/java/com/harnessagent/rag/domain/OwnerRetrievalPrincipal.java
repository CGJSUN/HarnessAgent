package com.harnessagent.rag.domain;

import com.harnessagent.runtime.PersonalRuntimeDefaults;
import java.util.Set;

public class OwnerRetrievalPrincipal {

    private final String scopeId;
    private final String ownerId;
    private final String agentId;

    public OwnerRetrievalPrincipal(String ownerId, String agentId) {
        this(PersonalRuntimeDefaults.PERSONAL_SCOPE_ID, ownerId, agentId);
    }

    public OwnerRetrievalPrincipal(String scopeId, String ownerId, String agentId) {
        this.scopeId = require(scopeId, "scopeId");
        this.ownerId = require(ownerId, "ownerId");
        this.agentId = agentId == null ? "" : agentId.trim();
    }

    public OwnerRetrievalPrincipal(
            String scopeId,
            String ownerId,
            String agentId,
            Set<String> ignoredOwnerHints,
            Set<String> ignoredGroupHints) {
        this(scopeId, ownerId, agentId);
    }

    public static OwnerRetrievalPrincipal forOwner(String ownerId, String agentId) {
        return new OwnerRetrievalPrincipal(ownerId, agentId);
    }

    public String scopeId() {
        return scopeId;
    }

    public String ownerId() {
        return ownerId;
    }

    public String agentId() {
        return agentId;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
