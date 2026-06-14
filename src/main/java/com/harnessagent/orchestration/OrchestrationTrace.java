package com.harnessagent.orchestration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OrchestrationTrace(
        String id,
        Instant occurredAt,
        String tenantId,
        String userId,
        String supervisorAgentId,
        String selectedAgentId,
        String taskIntent,
        double confidence,
        OrchestrationStatus status,
        List<String> candidateAgentIds,
        List<OrchestrationStep> steps,
        List<HandoffRecord> handoffs,
        Map<String, Object> attributes) {

    public OrchestrationTrace {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        candidateAgentIds = candidateAgentIds == null ? List.of() : List.copyOf(candidateAgentIds);
        steps = steps == null ? List.of() : List.copyOf(steps);
        handoffs = handoffs == null ? List.of() : List.copyOf(handoffs);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
