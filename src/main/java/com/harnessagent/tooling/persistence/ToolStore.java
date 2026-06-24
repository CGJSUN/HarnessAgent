package com.harnessagent.tooling.persistence;

import java.util.List;
import java.util.Optional;
import com.harnessagent.tooling.activity.ToolActivityRecord;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolPendingConfirmation;
import com.harnessagent.tooling.execution.ToolExecutionResult;

public interface ToolStore {

    ToolDefinition saveTool(ToolDefinition definition);

    Optional<ToolDefinition> findTool(String toolId);

    List<ToolDefinition> listTools(String ownerScopeId);

    void saveActivity(ToolActivityRecord record);

    List<ToolActivityRecord> listActivity(String ownerScopeId);

    Optional<ToolIdempotencyRecord> findIdempotentResult(String idempotencyKey);

    void saveIdempotentResult(String idempotencyKey, String parameterFingerprint, ToolExecutionResult result);

    ToolPendingConfirmation savePendingConfirmation(ToolPendingConfirmation confirmation);

    Optional<ToolPendingConfirmation> findPendingConfirmation(String confirmationId);

    boolean claimPendingConfirmation(String confirmationId, String decisionReason);

    List<ToolPendingConfirmation> listPendingConfirmations(
            String ownerScopeId,
            String ownerId,
            String agentId,
            String sessionId);
}
