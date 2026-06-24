package com.harnessagent.tooling.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.harnessagent.tooling.activity.ToolActivityRecord;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolPendingConfirmation;
import com.harnessagent.tooling.domain.ToolPendingConfirmationStatus;
import com.harnessagent.tooling.execution.ToolExecutionResult;

@Component
@Profile("!production")
public class InMemoryToolStore implements ToolStore {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final List<ToolActivityRecord> auditRecords = new CopyOnWriteArrayList<>();
    private final Map<String, ToolIdempotencyRecord> idempotentResults = new ConcurrentHashMap<>();
    private final Map<String, ToolPendingConfirmation> pendingConfirmations = new ConcurrentHashMap<>();

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
    public List<ToolDefinition> listTools(String ownerScopeId) {
        if (ownerScopeId == null || ownerScopeId.isBlank()) {
            throw new IllegalArgumentException("owner scope is required");
        }
        return tools.values().stream()
                .filter(tool -> tool.ownerScopeId().equals(ownerScopeId.trim()))
                .sorted(Comparator.comparing(ToolDefinition::name))
                .toList();
    }

    @Override
    public void saveActivity(ToolActivityRecord record) {
        auditRecords.add(record);
    }

    @Override
    public List<ToolActivityRecord> listActivity(String ownerScopeId) {
        if (ownerScopeId == null || ownerScopeId.isBlank()) {
            throw new IllegalArgumentException("owner scope is required");
        }
        return auditRecords.stream()
                .filter(record -> record.ownerScopeId().equals(ownerScopeId.trim()))
                .sorted(Comparator.comparing(ToolActivityRecord::occurredAt))
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

    @Override
    public ToolPendingConfirmation savePendingConfirmation(ToolPendingConfirmation confirmation) {
        pendingConfirmations.put(confirmation.confirmationId(), confirmation);
        return confirmation;
    }

    @Override
    public Optional<ToolPendingConfirmation> findPendingConfirmation(String confirmationId) {
        if (confirmationId == null || confirmationId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(pendingConfirmations.get(confirmationId.trim()));
    }

    @Override
    public boolean claimPendingConfirmation(String confirmationId, String decisionReason) {
        if (confirmationId == null || confirmationId.isBlank()) {
            return false;
        }
        java.util.concurrent.atomic.AtomicBoolean claimed = new java.util.concurrent.atomic.AtomicBoolean(false);
        pendingConfirmations.computeIfPresent(confirmationId.trim(), (ignored, pending) -> {
            if (pending.status() != ToolPendingConfirmationStatus.PENDING) {
                return pending;
            }
            claimed.set(true);
            return pending.confirmed(decisionReason == null ? "confirmed" : decisionReason);
        });
        return claimed.get();
    }

    @Override
    public List<ToolPendingConfirmation> listPendingConfirmations(
            String ownerScopeId,
            String ownerId,
            String agentId,
            String sessionId) {
        return pendingConfirmations.values().stream()
                .filter(confirmation -> confirmation.status() == ToolPendingConfirmationStatus.PENDING)
                .filter(confirmation -> confirmation.ownerScopeId().equals(ownerScopeId))
                .filter(confirmation -> confirmation.ownerId().equals(ownerId))
                .filter(confirmation -> confirmation.agentId().equals(agentId))
                .filter(confirmation -> confirmation.sessionId().equals(sessionId))
                .sorted(Comparator.comparing(ToolPendingConfirmation::createdAt))
                .toList();
    }
}
