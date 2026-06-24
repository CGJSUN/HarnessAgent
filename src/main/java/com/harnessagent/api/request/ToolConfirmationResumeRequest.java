package com.harnessagent.api.request;

import com.harnessagent.tooling.domain.ToolConfirmationAction;
import java.util.Map;

public record ToolConfirmationResumeRequest(
        String ownerId,
        String agentId,
        String sessionId,
        ToolConfirmationAction action,
        Map<String, Object> parameters,
        String idempotencyKey) {
}
