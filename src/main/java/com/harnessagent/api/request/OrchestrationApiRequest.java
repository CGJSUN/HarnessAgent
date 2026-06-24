package com.harnessagent.api.request;

import java.util.Map;
import com.harnessagent.orchestration.domain.DelegationMode;
import com.harnessagent.orchestration.domain.FailureStrategy;

public record OrchestrationApiRequest(
        String ownerId,
        String supervisorAgentId,
        String taskIntent,
        String task,
        Map<String, Object> context,
        DelegationMode delegationMode,
        FailureStrategy failureStrategy) {
}
