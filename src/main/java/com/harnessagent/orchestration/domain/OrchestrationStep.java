package com.harnessagent.orchestration.domain;

import java.util.Map;
import java.util.UUID;

public record OrchestrationStep(
        String id,
        String agentId,
        String action,
        Map<String, Object> input,
        Map<String, Object> output,
        OrchestrationStatus status) {

    public OrchestrationStep {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        input = input == null ? Map.of() : Map.copyOf(input);
        output = output == null ? Map.of() : Map.copyOf(output);
        status = status == null ? OrchestrationStatus.PLANNED : status;
    }
}
