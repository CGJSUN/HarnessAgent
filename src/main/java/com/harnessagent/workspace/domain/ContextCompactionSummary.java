package com.harnessagent.workspace.domain;

import java.time.Instant;
import java.util.List;

public record ContextCompactionSummary(
        String id,
        String ownerId,
        String agentId,
        String sessionId,
        String uri,
        String goal,
        String currentState,
        List<String> keyFindings,
        List<String> decisions,
        List<String> fileReferences,
        List<String> nextSteps,
        List<String> sourceMessageIds,
        List<String> retainedMessageIds,
        Instant createdAt) {

    public ContextCompactionSummary {
        id = require(id, "id");
        ownerId = require(ownerId, "ownerId");
        agentId = require(agentId, "agentId");
        sessionId = require(sessionId, "sessionId");
        uri = require(uri, "uri");
        goal = clean(goal, "No explicit goal captured.");
        currentState = clean(currentState, "No current state captured.");
        keyFindings = safeList(keyFindings);
        decisions = safeList(decisions);
        fileReferences = safeList(fileReferences);
        nextSteps = safeList(nextSteps);
        sourceMessageIds = safeList(sourceMessageIds);
        retainedMessageIds = safeList(retainedMessageIds);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public String asPrompt() {
        return """
                Context compaction summary.
                Goal: %s
                Current state: %s
                Key findings:
                %s
                Decisions:
                %s
                File references:
                %s
                Next steps:
                %s
                Original context reference: %s
                """.formatted(
                goal,
                currentState,
                bullets(keyFindings),
                bullets(decisions),
                bullets(fileReferences),
                bullets(nextSteps),
                uri);
    }

    private static String bullets(List<String> values) {
        if (values.isEmpty()) {
            return "- None captured.";
        }
        return values.stream()
                .map(value -> "- " + value)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- None captured.");
    }

    private static List<String> safeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .limit(12)
                .toList();
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
