package com.harnessagent.api.request;

import java.util.Set;

public record KnowledgeRetrievalRequest(
        String tenantId,
        String userId,
        Set<String> departments,
        Set<String> roles,
        String query,
        int limit) {
}
