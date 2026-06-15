package com.harnessagent.tooling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!production")
public class InMemoryToolStore implements ToolStore {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final List<ToolAuditRecord> auditRecords = new CopyOnWriteArrayList<>();
    private final Map<String, ToolIdempotencyRecord> idempotentResults = new ConcurrentHashMap<>();

    @Override
    public ToolDefinition saveTool(ToolDefinition definition) {
        tools.put(definition.id(), definition);
        return definition;
    }

    @Override
    public Optional<ToolDefinition> findTool(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(toolId.trim()));
    }

    @Override
    public List<ToolDefinition> listTools(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return tools.values().stream()
                .filter(tool -> tool.tenantId().equals(tenantId.trim()))
                .sorted(Comparator.comparing(ToolDefinition::name))
                .toList();
    }

    @Override
    public void saveAudit(ToolAuditRecord record) {
        auditRecords.add(record);
    }

    @Override
    public List<ToolAuditRecord> listAudit(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return auditRecords.stream()
                .filter(record -> record.tenantId().equals(tenantId.trim()))
                .sorted(Comparator.comparing(ToolAuditRecord::occurredAt))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public Optional<ToolIdempotencyRecord> findIdempotentResult(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(idempotentResults.get(idempotencyKey));
    }

    @Override
    public void saveIdempotentResult(String idempotencyKey, String parameterFingerprint, ToolExecutionResult result) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotentResults.put(idempotencyKey,
                    new ToolIdempotencyRecord(idempotencyKey, parameterFingerprint, result));
        }
    }
}
