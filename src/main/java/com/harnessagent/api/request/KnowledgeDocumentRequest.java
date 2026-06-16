package com.harnessagent.api.request;

import com.harnessagent.rag.domain.KnowledgeVisibility;
import java.util.Set;

public record KnowledgeDocumentRequest(
        String tenantId,
        String ownerId,
        String title,
        String version,
        KnowledgeVisibility visibility,
        Set<String> allowedDepartments,
        Set<String> allowedRoles,
        Set<String> allowedUsers,
        String updatePolicy,
        String content) {
}
