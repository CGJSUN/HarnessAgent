package com.harnessagent.rag.domain;

import java.util.Set;

public record KnowledgeSourceRegistration(
        String tenantId,
        String ownerId,
        String title,
        String version,
        KnowledgeVisibility visibility,
        Set<String> allowedDepartments,
        Set<String> allowedRoles,
        Set<String> allowedUsers,
        String updatePolicy) {
}
