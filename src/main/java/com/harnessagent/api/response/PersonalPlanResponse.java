package com.harnessagent.api.response;

import com.harnessagent.workspace.domain.PersonalPlan;
import java.time.Instant;
import java.util.List;

public record PersonalPlanResponse(
        String id,
        String ownerId,
        String agentId,
        String sessionId,
        String goal,
        List<String> steps,
        String uri,
        Instant createdAt,
        String status,
        String currentStep,
        List<String> blockers) {

    public static PersonalPlanResponse from(PersonalPlan plan) {
        String currentStep = plan.steps().isEmpty() ? "" : plan.steps().get(0);
        return new PersonalPlanResponse(
                plan.id(),
                plan.ownerId(),
                plan.agentId(),
                plan.sessionId(),
                plan.goal(),
                plan.steps(),
                plan.uri(),
                plan.createdAt(),
                "IN_PROGRESS",
                currentStep,
                List.of());
    }
}
