package com.harnessagent.tooling.application;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.telemetry.RuntimeTelemetry;
import com.harnessagent.production.infrastructure.RuntimeTimeoutGuard;
import com.harnessagent.production.telemetry.TelemetryEventType;
import com.harnessagent.security.application.PromptInjectionGuard;
import com.harnessagent.security.application.SafeLogFields;
import com.harnessagent.security.domain.SecurityDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.harnessagent.tooling.audit.ToolAuditRecord;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolExecutionStatus;
import com.harnessagent.tooling.domain.ToolRegistration;
import com.harnessagent.tooling.domain.ToolRiskLevel;
import com.harnessagent.tooling.domain.ToolSourceType;
import com.harnessagent.tooling.execution.DefaultToolExecutor;
import com.harnessagent.tooling.execution.ToolExecutionCommand;
import com.harnessagent.tooling.execution.ToolExecutionResult;
import com.harnessagent.tooling.execution.ToolExecutor;
import com.harnessagent.tooling.persistence.ToolIdempotencyRecord;
import com.harnessagent.tooling.persistence.ToolStore;

@Service
public class ToolService {

    private static final Logger log = LoggerFactory.getLogger(ToolService.class);
    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> COMMON_SENSITIVE_KEYS = Set.of(
            "token", "password", "secret", "apikey", "api_key", "authorization");

    private final ToolStore store;
    private final List<ToolExecutor> executors;
    private final RuntimeTimeoutGuard timeoutGuard;
    private final RuntimeTelemetry telemetry;
    private final PromptInjectionGuard promptInjectionGuard;

    public ToolService(ToolStore store, List<ToolExecutor> executors) {
        this(store,
                executors,
                new RuntimeTimeoutGuard(new ProductionRuntimeProperties()),
                RuntimeTelemetry.noop(),
                new PromptInjectionGuard());
    }

    @Autowired
    public ToolService(
            ToolStore store,
            List<ToolExecutor> executors,
            RuntimeTimeoutGuard timeoutGuard,
            RuntimeTelemetry telemetry,
            PromptInjectionGuard promptInjectionGuard) {
        this.store = store;
        this.executors = executors == null || executors.isEmpty()
                ? List.of(new DefaultToolExecutor())
                : List.copyOf(executors);
        this.timeoutGuard = timeoutGuard;
        this.telemetry = telemetry;
        this.promptInjectionGuard = promptInjectionGuard;
    }

    public ToolDefinition registerTool(ToolRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("registration is required");
        }
        Instant now = Instant.now();
        return registerTool(new ToolDefinition(
                null,
                registration.tenantId(),
                registration.name(),
                registration.description(),
                registration.ownerSystem(),
                registration.ownerId(),
                registration.sourceType(),
                registration.sourceRef(),
                registration.riskLevel(),
                registration.mutating(),
                registration.enabled(),
                registration.parameterSchema(),
                registration.permissionPolicy(),
                registration.auditPolicy(),
                now,
                now));
    }

    public ToolDefinition registerTool(ToolDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition is required");
        }
        return store.saveTool(definition);
    }

    public List<ToolDefinition> listTools(String tenantId) {
        return store.listTools(tenantId);
    }

    public ToolDefinition setEnabled(String toolId, boolean enabled) {
        ToolDefinition tool = store.findTool(toolId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolId));
        return store.saveTool(tool.withEnabled(enabled));
    }

    public ToolExecutionResult execute(ToolExecutionCommand command) {
        Instant startedAt = Instant.now();
        Optional<ToolDefinition> found = store.findTool(command.toolId());
        if (found.isEmpty()) {
            ToolExecutionResult result = ToolExecutionResult.denied(command.toolId(), "Unknown tool.");
            log.warn(
                    "tool unknown tenantId={} agentId={} toolId={} userHash={} sessionHash={}",
                    command.tenantId(),
                    command.agentId(),
                    command.toolId(),
                    SafeLogFields.user(command.userId()),
                    SafeLogFields.session(command.sessionId()));
            auditUnknown(command, result, startedAt);
            recordToolTelemetry(command, "", result, startedAt);
            return result;
        }

        ToolDefinition tool = found.get();
        // Preflight is ordered before approval/idempotency/execution so rejected calls are still audited but never run.
        ToolExecutionResult rejected = preflight(command, tool);
        if (rejected != null) {
            log.warn(
                    "tool preflight rejected tenantId={} agentId={} toolId={} userHash={} sessionHash={} reason={}",
                    command.tenantId(),
                    command.agentId(),
                    tool.id(),
                    SafeLogFields.user(command.userId()),
                    SafeLogFields.session(command.sessionId()),
                    SafeLogFields.reasonCode(rejected.message()));
            audit(command, tool, rejected, startedAt, rejected.message());
            recordToolTelemetry(command, tool.name(), rejected, startedAt);
            return rejected;
        }

        if (tool.riskLevel() == ToolRiskLevel.HIGH_RISK && !isApproved(command)) {
            // High-risk operations stop here until confirmed; idempotency and execution are intentionally later.
            ToolExecutionResult result = ToolExecutionResult.pending(
                    tool.id(),
                    Map.of(
                            "toolName", tool.name(),
                            "riskLevel", tool.riskLevel().name(),
                            "parameters", sanitizeInput(tool, command.parameters())));
            log.info(
                    "tool high_risk pending tenantId={} agentId={} toolId={} userHash={} sessionHash={} idempotencyHash={}",
                    command.tenantId(),
                    command.agentId(),
                    tool.id(),
                    SafeLogFields.user(command.userId()),
                    SafeLogFields.session(command.sessionId()),
                    SafeLogFields.idempotency(command.idempotencyKey()));
            audit(command, tool, result, startedAt, result.message());
            recordToolTelemetry(command, tool.name(), result, startedAt);
            return result;
        }

        // Idempotency is checked immediately before execution and saved only after a real executor result.
        String idempotencyKey = idempotencyKey(tool, command);
        String parameterFingerprint = parameterFingerprint(command.parameters());
        if (idempotencyKey != null) {
            Optional<ToolIdempotencyRecord> previous = store.findIdempotentResult(idempotencyKey);
            if (previous.isPresent()) {
                if (!previous.get().parameterFingerprint().equals(parameterFingerprint)) {
                    ToolExecutionResult conflict = ToolExecutionResult.idempotencyConflict(tool.id());
                    log.warn(
                            "tool idempotency conflict tenantId={} agentId={} toolId={} userHash={} sessionHash={} idempotencyHash={}",
                            command.tenantId(),
                            command.agentId(),
                            tool.id(),
                            SafeLogFields.user(command.userId()),
                            SafeLogFields.session(command.sessionId()),
                            SafeLogFields.idempotency(command.idempotencyKey()));
                    audit(command, tool, conflict, startedAt, conflict.message());
                    recordToolTelemetry(command, tool.name(), conflict, startedAt);
                    return conflict;
                }
                ToolExecutionResult duplicate = ToolExecutionResult.duplicate(tool.id(), previous.get().result());
                log.info(
                        "tool idempotency reused tenantId={} agentId={} toolId={} userHash={} sessionHash={} idempotencyHash={}",
                        command.tenantId(),
                        command.agentId(),
                        tool.id(),
                        SafeLogFields.user(command.userId()),
                        SafeLogFields.session(command.sessionId()),
                        SafeLogFields.idempotency(command.idempotencyKey()));
                audit(command, tool, duplicate, startedAt, duplicate.message());
                recordToolTelemetry(command, tool.name(), duplicate, startedAt);
                return duplicate;
            }
        }

        ToolExecutionResult result = runExecutor(tool, command);
        if (idempotencyKey != null && result.status() != ToolExecutionStatus.DUPLICATE) {
            store.saveIdempotentResult(idempotencyKey, parameterFingerprint, result);
        }
        if (result.status() == ToolExecutionStatus.FAILED) {
            log.warn(
                    "tool execution failed tenantId={} agentId={} toolId={} userHash={} sessionHash={} reason={}",
                    command.tenantId(),
                    command.agentId(),
                    tool.id(),
                    SafeLogFields.user(command.userId()),
                    SafeLogFields.session(command.sessionId()),
                    SafeLogFields.reasonCode(result.message()));
        }
        audit(command, tool, result, startedAt, result.status() == ToolExecutionStatus.FAILED ? result.message() : "");
        recordToolTelemetry(command, tool.name(), result, startedAt);
        return result;
    }

    public ToolExecutionResult reject(ToolExecutionCommand command) {
        Instant startedAt = Instant.now();
        Optional<ToolDefinition> found = store.findTool(command.toolId());
        if (found.isEmpty()) {
            ToolExecutionResult result = ToolExecutionResult.denied(command.toolId(), "Unknown tool.");
            log.warn(
                    "tool reject unknown tenantId={} agentId={} toolId={} userHash={} sessionHash={}",
                    command.tenantId(),
                    command.agentId(),
                    command.toolId(),
                    SafeLogFields.user(command.userId()),
                    SafeLogFields.session(command.sessionId()));
            auditUnknown(command, result, startedAt);
            recordToolTelemetry(command, "", result, startedAt);
            return result;
        }
        ToolDefinition tool = found.get();
        ToolExecutionResult rejected = preflight(command, tool);
        if (rejected == null) {
            rejected = ToolExecutionResult.denied(tool.id(), "High-risk tool operation rejected by user.");
        }
        audit(command, tool, rejected, startedAt, rejected.message());
        recordToolTelemetry(command, tool.name(), rejected, startedAt);
        return rejected;
    }

    public List<ToolAuditRecord> listAudit(String tenantId) {
        return store.listAudit(tenantId);
    }

    private ToolExecutionResult preflight(ToolExecutionCommand command, ToolDefinition tool) {
        if (!tool.enabled()) {
            return ToolExecutionResult.denied(tool.id(), "Tool is disabled.");
        }
        if (!tool.tenantId().equals(command.tenantId())) {
            return ToolExecutionResult.denied(tool.id(), "Tool does not belong to this tenant.");
        }
        if (!tool.permissionPolicy().permits(command.principal())) {
            return ToolExecutionResult.denied(tool.id(), "User, tenant, Agent, or role is not allowed to use this tool.");
        }
        Optional<String> parameterError = tool.parameterSchema().validate(command.parameters());
        if (parameterError.isPresent()) {
            return ToolExecutionResult.denied(tool.id(), parameterError.get());
        }
        SecurityDecision toolSafety = promptInjectionGuard.inspectToolParameters(
                command.parameters(),
                tool.parameterSchema().allowedParameters());
        if (!toolSafety.allowed()) {
            return ToolExecutionResult.denied(tool.id(), toolSafety.reason());
        }
        if (tool.mutating() && command.idempotencyKey() == null) {
            return ToolExecutionResult.denied(tool.id(), "Idempotency key is required for mutating tools.");
        }
        return null;
    }

    private ToolExecutionResult runExecutor(ToolDefinition tool, ToolExecutionCommand command) {
        try {
            ToolExecutor executor = executors.stream()
                    .filter(candidate -> candidate.supports(tool))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No executor supports tool: " + tool.name()));
            Map<String, Object> output = timeoutGuard.guardTool(Mono.fromCallable(
                    () -> executor.execute(tool, command.parameters()))).block();
            return ToolExecutionResult.success(tool.id(), output);
        } catch (RuntimeException exception) {
            return ToolExecutionResult.failed(tool.id(), exception.getMessage());
        }
    }

    private boolean isApproved(ToolExecutionCommand command) {
        return command.confirmed()
                || (command.approvalId() != null && command.reviewerId() != null);
    }

    private String idempotencyKey(ToolDefinition tool, ToolExecutionCommand command) {
        if (!tool.mutating()) {
            return null;
        }
        return command.tenantId() + ":" + tool.id() + ":" + command.idempotencyKey();
    }

    private void auditUnknown(ToolExecutionCommand command, ToolExecutionResult result, Instant startedAt) {
        store.saveAudit(new ToolAuditRecord(
                null,
                Instant.now(),
                command.tenantId(),
                command.userId(),
                command.agentId(),
                command.sessionId(),
                command.toolId(),
                "",
                ToolSourceType.INTERNAL,
                ToolRiskLevel.READ_ONLY,
                result.status(),
                sanitize(command.parameters(), COMMON_SENSITIVE_KEYS),
                sanitize(result.output(), COMMON_SENSITIVE_KEYS),
                durationMillis(startedAt),
                command.approvalId(),
                command.reviewerId(),
                command.idempotencyKey(),
                result.message()));
    }

    private void audit(
            ToolExecutionCommand command,
            ToolDefinition tool,
            ToolExecutionResult result,
            Instant startedAt,
            String failureReason) {
        if (!tool.auditPolicy().enabled()) {
            return;
        }
        store.saveAudit(new ToolAuditRecord(
                null,
                Instant.now(),
                command.tenantId(),
                command.userId(),
                command.agentId(),
                command.sessionId(),
                tool.id(),
                tool.name(),
                tool.sourceType(),
                tool.riskLevel(),
                result.status(),
                sanitizeInput(tool, command.parameters()),
                sanitizeOutput(tool, result.output()),
                durationMillis(startedAt),
                command.approvalId(),
                command.reviewerId(),
                command.idempotencyKey(),
                failureReason));
    }

    private Map<String, Object> sanitizeInput(ToolDefinition tool, Map<String, Object> input) {
        Set<String> sensitive = union(
                tool.parameterSchema().sensitiveParameters(),
                tool.auditPolicy().sensitiveParameters());
        return sanitize(input, sensitive);
    }

    private Map<String, Object> sanitizeOutput(ToolDefinition tool, Map<String, Object> output) {
        Set<String> sensitive = union(
                union(tool.parameterSchema().sensitiveParameters(), tool.auditPolicy().sensitiveParameters()),
                tool.auditPolicy().sensitiveResultFields());
        return sanitize(output, sensitive);
    }

    private static Map<String, Object> sanitize(Map<String, Object> input, Set<String> sensitiveKeys) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        input.forEach((key, value) ->
                sanitized.put(key, isSensitiveKey(key, sensitiveKeys) ? REDACTED : sanitizeValue(value, sensitiveKeys)));
        return Map.copyOf(sanitized);
    }

    private static Object sanitizeValue(Object value, Set<String> sensitiveKeys) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                if (key != null) {
                    String stringKey = String.valueOf(key);
                    nested.put(stringKey,
                            isSensitiveKey(stringKey, sensitiveKeys)
                                    ? REDACTED
                                    : sanitizeValue(nestedValue, sensitiveKeys));
                }
            });
            return Map.copyOf(nested);
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.ArrayList<Object> sanitized = new java.util.ArrayList<>();
            iterable.forEach(item -> sanitized.add(sanitizeValue(item, sensitiveKeys)));
            return List.copyOf(sanitized);
        }
        return value;
    }

    private static boolean isSensitiveKey(String key, Set<String> sensitiveKeys) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        return sensitiveKeys.stream()
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }

    private static String parameterFingerprint(Map<String, Object> parameters) {
        return canonicalValue(parameters);
    }

    private static String canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .map(entry -> String.valueOf(entry.getKey()) + "=" + canonicalValue(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.ArrayList<String> values = new java.util.ArrayList<>();
            iterable.forEach(item -> values.add(canonicalValue(item)));
            return values.stream().collect(Collectors.joining(",", "[", "]"));
        }
        return String.valueOf(value);
    }

    private static long durationMillis(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }

    private void recordToolTelemetry(
            ToolExecutionCommand command,
            String toolName,
            ToolExecutionResult result,
            Instant startedAt) {
        telemetry.record(
                TelemetryEventType.TOOL,
                command.tenantId(),
                command.userId(),
                command.agentId(),
                "tool-service",
                Duration.between(startedAt, Instant.now()),
                Map.of(
                        "toolName", toolName,
                        "status", result.status().name(),
                        "approvalRequired", result.approvalRequired()));
    }
}
