package com.harnessagent.workspace.domain;

import java.time.Instant;
import java.util.List;

public record PersonalPlan(
        String id,
        String ownerId,
        String agentId,
        String sessionId,
        String goal,
        List<String> steps,
        String uri,
        Instant createdAt) {

    public PersonalPlan {
        id = require(id, "id");
        ownerId = require(ownerId, "ownerId");
        agentId = require(agentId, "agentId");
        sessionId = require(sessionId, "sessionId");
        goal = require(goal, "goal");
        steps = steps == null ? List.of() : steps.stream()
                .filter(step -> step != null && !step.isBlank())
                .map(String::trim)
                .toList();
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("plan steps are required");
        }
        uri = require(uri, "uri");
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
