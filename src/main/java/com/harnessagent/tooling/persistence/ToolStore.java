package com.harnessagent.tooling.persistence;

import java.util.List;
import java.util.Optional;
import com.harnessagent.tooling.audit.ToolAuditRecord;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.execution.ToolExecutionResult;

public interface ToolStore {

    ToolDefinition saveTool(ToolDefinition definition);

    Optional<ToolDefinition> findTool(String toolId);

    List<ToolDefinition> listTools(String tenantId);

    void saveAudit(ToolAuditRecord record);

    List<ToolAuditRecord> listAudit(String tenantId);

    Optional<ToolIdempotencyRecord> findIdempotentResult(String idempotencyKey);

    void saveIdempotentResult(String idempotencyKey, String parameterFingerprint, ToolExecutionResult result);
}
