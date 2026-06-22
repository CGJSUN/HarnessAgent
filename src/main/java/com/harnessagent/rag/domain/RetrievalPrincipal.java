package com.harnessagent.rag.domain;

import java.util.Set;

public record RetrievalPrincipal(
        String tenantId, String userId, String agentId, Set<String> departments, Set<String> roles) {

    public RetrievalPrincipal(String tenantId, String userId, Set<String> departments, Set<String> roles) {
        this(tenantId, userId, "", departments, roles);
    }

    public RetrievalPrincipal {
        agentId = agentId == null ? "" : agentId;
    }
}
