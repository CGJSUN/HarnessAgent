package com.harnessagent.orchestration.domain;

import com.harnessagent.security.domain.SecurityPrincipal;
import java.util.Map;

public record OrchestrationRequest(
        SecurityPrincipal principal,
        String supervisorAgentId,
        String taskIntent,
        String task,
        Map<String, Object> context,
        DelegationMode delegationMode,
        FailureStrategy failureStrategy) {

    public OrchestrationRequest {
        context = context == null ? Map.of() : Map.copyOf(context);
        delegationMode = delegationMode == null ? DelegationMode.SYNC : delegationMode;
        failureStrategy = failureStrategy == null ? FailureStrategy.STOP : failureStrategy;
    }

    public OrchestrationRequest(
            SecurityPrincipal principal,
            String supervisorAgentId,
            String taskIntent,
            String task,
            Map<String, Object> context) {
        this(principal, supervisorAgentId, taskIntent, task, context, DelegationMode.SYNC, FailureStrategy.STOP);
    }
}
