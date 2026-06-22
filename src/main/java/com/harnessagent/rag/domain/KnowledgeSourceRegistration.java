package com.harnessagent.rag.domain;

import java.util.Set;

public record KnowledgeSourceRegistration(
        String tenantId,
        String ownerId,
        String agentId,
        String title,
        String version,
        KnowledgeVisibility visibility,
        Set<String> allowedDepartments,
        Set<String> allowedRoles,
        Set<String> allowedUsers,
        String updatePolicy,
        KnowledgeSourceType sourceType,
        String sourceUri) {

    public KnowledgeSourceRegistration(
            String tenantId,
            String ownerId,
            String title,
            String version,
            KnowledgeVisibility visibility,
            Set<String> allowedDepartments,
            Set<String> allowedRoles,
            Set<String> allowedUsers,
            String updatePolicy) {
        this(
                tenantId,
                ownerId,
                "",
                title,
                version,
                visibility,
                allowedDepartments,
                allowedRoles,
                allowedUsers,
                updatePolicy,
                KnowledgeSourceType.INLINE_TEXT,
                "");
    }

    public KnowledgeSourceRegistration {
        agentId = agentId == null ? "" : agentId;
    }
}
