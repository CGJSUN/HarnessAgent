package com.harnessagent.api.request;

import com.harnessagent.tooling.domain.ToolConfirmationAction;
import java.util.Map;
import java.util.Set;

public record ToolConfirmationResumeRequest(
        String tenantId,
        String userId,
        String agentId,
        String sessionId,
        ToolConfirmationAction action,
        Map<String, Object> parameters,
        Set<String> departments,
        Set<String> roles,
        String approvalId,
        String reviewerId,
        String idempotencyKey) {
}
