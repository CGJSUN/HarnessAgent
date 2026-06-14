package com.harnessagent.orchestration;

import com.harnessagent.security.SecurityPrincipal;
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
