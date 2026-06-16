package com.harnessagent.rag.domain;

import java.util.Set;

public record RetrievalPrincipal(
        String tenantId, String userId, Set<String> departments, Set<String> roles) {
}
