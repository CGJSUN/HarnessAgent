package com.harnessagent.orchestration.domain;

import com.harnessagent.security.domain.SecurityPrincipal;
import java.util.Map;

public record OrchestrationRequest(
        SecurityPrincipal principal,
        String supervisorAgentId,
        String taskIntent,
        String task,
        Map<String, Object> context) {

    public OrchestrationRequest {
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
