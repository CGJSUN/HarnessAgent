package com.harnessagent.tooling;

import java.util.List;
import java.util.Optional;

public interface ToolStore {

    ToolDefinition saveTool(ToolDefinition definition);

    Optional<ToolDefinition> findTool(String toolId);

    List<ToolDefinition> listTools(String tenantId);

    void saveAudit(ToolAuditRecord record);

    List<ToolAuditRecord> listAudit(String tenantId);

    Optional<ToolIdempotencyRecord> findIdempotentResult(String idempotencyKey);

    void saveIdempotentResult(String idempotencyKey, String parameterFingerprint, ToolExecutionResult result);
}
