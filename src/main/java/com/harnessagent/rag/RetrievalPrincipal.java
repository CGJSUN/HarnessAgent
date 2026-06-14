package com.harnessagent.rag;

import java.util.Set;

public record RetrievalPrincipal(
        String tenantId, String userId, Set<String> departments, Set<String> roles) {
}
