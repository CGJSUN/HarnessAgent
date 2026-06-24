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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.config.AgentWorkloadType;
import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.production.sandbox.DockerSandboxExecutor;
import com.harnessagent.production.sandbox.LocalProcessSandboxExecutor;
import com.harnessagent.production.sandbox.RemoteSandboxExecutor;
import com.harnessagent.production.sandbox.SandboxExecutionMode;
import com.harnessagent.production.sandbox.SandboxExecutionPolicy;
import com.harnessagent.production.sandbox.SandboxExecutionPolicyService;
import com.harnessagent.production.sandbox.SandboxExecutionRequest;
import com.harnessagent.production.sandbox.SandboxExecutionResult;
import com.harnessagent.production.sandbox.SandboxExecutionStatus;
import com.harnessagent.production.sandbox.SandboxExecutor;
import com.harnessagent.production.sandbox.SandboxExecutorRegistry;
import com.harnessagent.production.telemetry.RuntimeTelemetry;
import com.harnessagent.production.infrastructure.RuntimeTimeoutGuard;
import com.harnessagent.production.telemetry.TelemetryEventType;
import com.harnessagent.runtime.OwnerScope;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.security.application.PromptInjectionGuard;
import com.harnessagent.security.application.SafeLogFields;
import com.harnessagent.security.domain.SecurityDecision;
import com.harnessagent.workspace.application.PersonalWorkspaceService;
import com.harnessagent.workspace.application.PlanModeService;
import com.harnessagent.workspace.domain.PersonalWorkspaceLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.harnessagent.tooling.activity.ToolActivityRecord;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolExecutionStatus;
import com.harnessagent.tooling.domain.ToolConfirmationAction;
import com.harnessagent.tooling.domain.ToolPendingConfirmation;
import com.harnessagent.tooling.domain.ToolPendingConfirmationStatus;
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
    private static final Set<String> EXECUTION_PARAMETER_NAMES = Set.of("command", "code", "script", "sql", "query");
    private static final Set<String> COMMON_SENSITIVE_KEYS = Set.of(
            "token", "password", "secret", "apikey", "api_key", "authorization");

    private final ToolStore store;
    private final List<ToolExecutor> executors;
    private final RuntimeTimeoutGuard timeoutGuard;
    private final RuntimeTelemetry telemetry;
    private final PromptInjectionGuard promptInjectionGuard;
    private final SandboxExecutionPolicyService sandboxPolicyService;
    private final SandboxExecutorRegistry sandboxExecutorRegistry;
    private final PersonalWorkspaceService personalWorkspaceService;

    public ToolService(ToolStore store, List<ToolExecutor> executors) {
        this(store,
                executors,
                new RuntimeTimeoutGuard(new ProductionRuntimeProperties()),
                RuntimeTelemetry.noop(),
                new PromptInjectionGuard(),
                new SandboxExecutionPolicyService(new ProductionRuntimeProperties()),
                new SandboxExecutorRegistry(List.of(
                        new LocalProcessSandboxExecutor(),
                        new DockerSandboxExecutor(),
                        new RemoteSandboxExecutor())),
                new PersonalWorkspaceService(new HarnessAgentProperties()));
    }

    public ToolService(
            ToolStore store,
            List<ToolExecutor> executors,
            RuntimeTimeoutGuard timeoutGuard,
            RuntimeTelemetry telemetry,
            PromptInjectionGuard promptInjectionGuard,
            SandboxExecutionPolicyService sandboxPolicyService,
            SandboxExecutorRegistry sandboxExecutorRegistry) {
        this(
                store,
                executors,
                timeoutGuard,
                telemetry,
                promptInjectionGuard,
                sandboxPolicyService,
                sandboxExecutorRegistry,
                new PersonalWorkspaceService(new HarnessAgentProperties()));
    }

    @Autowired
    public ToolService(
            ToolStore store,
            List<ToolExecutor> executors,
            RuntimeTimeoutGuard timeoutGuard,
            RuntimeTelemetry telemetry,
            PromptInjectionGuard promptInjectionGuard,
            SandboxExecutionPolicyService sandboxPolicyService,
            SandboxExecutorRegistry sandboxExecutorRegistry,
            PersonalWorkspaceService personalWorkspaceService) {
        this.store = store;
        this.executors = executors == null || executors.isEmpty()
                ? List.of(new DefaultToolExecutor())
                : List.copyOf(executors);
        this.timeoutGuard = timeoutGuard;
        this.telemetry = telemetry;
        this.promptInjectionGuard = promptInjectionGuard;
        this.sandboxPolicyService = sandboxPolicyService;
        this.sandboxExecutorRegistry = sandboxExecutorRegistry;
        this.personalWorkspaceService = personalWorkspaceService;
    }

    public ToolDefinition registerTool(ToolRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("registration is required");
        }
        Instant now = Instant.now();
        return registerTool(new ToolDefinition(
                null,
                registration.ownerScopeId(),
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
                registration.outputSchema(),
                registration.permissionPolicy(),
                registration.activityPolicy(),
                registration.workloadType(),
                now,
                now));
    }

    public ToolDefinition registerTool(ToolDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition is required");
        }
        return store.saveTool(definition);
    }

    public List<ToolDefinition> listTools(String ownerScopeId) {
        return store.listTools(ownerScopeId);
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
                    "tool unknown ownerHash={} agentId={} toolId={} sessionHash={}",
                    SafeLogFields.owner(command.ownerId()),
                    command.agentId(),
                    command.toolId(),
                    SafeLogFields.session(command.sessionId()));
            auditUnknown(command, result, startedAt);
            recordToolTelemetry(command, "", result, startedAt);
            return result;
        }

        ToolDefinition tool = found.get();
        boolean planMode = PlanModeService.planModeRequested(command.parameters());
        Map<String, Object> executionParameters = PlanModeService.stripPlanModeParameter(command.parameters());
        ToolExecutionCommand effectiveCommand = planMode
                ? new ToolExecutionCommand(
                        command.ownerScopeId(),
                        command.ownerId(),
                        command.agentId(),
                        command.sessionId(),
                        command.toolId(),
                        executionParameters,
                        command.confirmed(),
                        command.idempotencyKey())
                : command;
        ToolExecutionResult planModeRejection = planMode ? planModeRejection(effectiveCommand, tool) : null;
        if (planModeRejection != null) {
            recordActivity(effectiveCommand, tool, planModeRejection, startedAt, planModeRejection.message());
            recordToolTelemetry(effectiveCommand, tool.name(), planModeRejection, startedAt);
            return planModeRejection;
        }
        // Preflight is ordered before approval/idempotency/execution so rejected calls are still audited but never run.
        ToolExecutionResult rejected = preflight(effectiveCommand, tool);
        if (rejected != null) {
            log.warn(
                    "tool preflight rejected ownerHash={} agentId={} toolId={} sessionHash={} reason={}",
                    SafeLogFields.owner(effectiveCommand.ownerId()),
                    effectiveCommand.agentId(),
                    tool.id(),
                    SafeLogFields.session(effectiveCommand.sessionId()),
                    SafeLogFields.reasonCode(rejected.message()));
            recordActivity(effectiveCommand, tool, rejected, startedAt, rejected.message());
            recordToolTelemetry(effectiveCommand, tool.name(), rejected, startedAt);
            return rejected;
        }

        String idempotencyKey = idempotencyKey(tool, effectiveCommand);
        String parameterFingerprint = parameterFingerprint(effectiveCommand.parameters());
        if (idempotencyKey != null) {
            Optional<ToolIdempotencyRecord> previous = store.findIdempotentResult(idempotencyKey);
            if (previous.isPresent()) {
                if (!previous.get().parameterFingerprint().equals(parameterFingerprint)) {
                    ToolExecutionResult conflict = ToolExecutionResult.idempotencyConflict(tool.id());
                    log.warn(
                            "tool idempotency conflict ownerHash={} agentId={} toolId={} sessionHash={} idempotencyHash={}",
                            SafeLogFields.owner(effectiveCommand.ownerId()),
                            effectiveCommand.agentId(),
                            tool.id(),
                            SafeLogFields.session(effectiveCommand.sessionId()),
                            SafeLogFields.idempotency(effectiveCommand.idempotencyKey()));
                    recordActivity(effectiveCommand, tool, conflict, startedAt, conflict.message());
                    recordToolTelemetry(effectiveCommand, tool.name(), conflict, startedAt);
                    return conflict;
                }
                ToolExecutionResult duplicate = ToolExecutionResult.duplicate(tool.id(), previous.get().result());
                log.info(
                        "tool idempotency reused ownerHash={} agentId={} toolId={} sessionHash={} idempotencyHash={}",
                        SafeLogFields.owner(effectiveCommand.ownerId()),
                        effectiveCommand.agentId(),
                        tool.id(),
                        SafeLogFields.session(effectiveCommand.sessionId()),
                        SafeLogFields.idempotency(effectiveCommand.idempotencyKey()));
                recordActivity(effectiveCommand, tool, duplicate, startedAt, duplicate.message());
                recordToolTelemetry(effectiveCommand, tool.name(), duplicate, startedAt);
                return duplicate;
            }
        }

        Optional<AgentWorkloadType> sandboxWorkload = sandboxWorkload(tool);
        if (sandboxWorkload.isPresent()) {
            ToolExecutionResult approvalRejection = approvalRejection(effectiveCommand, tool, parameterFingerprint);
            if (approvalRejection != null) {
                recordActivity(effectiveCommand, tool, approvalRejection, startedAt, approvalRejection.message());
                recordToolTelemetry(effectiveCommand, tool.name(), approvalRejection, startedAt);
                return approvalRejection;
            }
        }
        if (sandboxWorkload.isPresent() && !approvalRequested(effectiveCommand)) {
            ToolPendingConfirmation pending = markPendingConfirmation(
                    effectiveCommand,
                    tool,
                    parameterFingerprint,
                    Map.of(
                            "toolName", tool.name(),
                            "riskLevel", tool.riskLevel().name(),
                            "sandboxRequired", true,
                            "workloadType", sandboxWorkload.get().name(),
                            "parameters", sanitizeInput(tool, effectiveCommand.parameters())));
            ToolExecutionResult result = ToolExecutionResult.pending(tool.id(), pending.operationSummary());
            log.info(
                    "tool sandbox pending ownerHash={} agentId={} toolId={} workloadType={} sessionHash={} idempotencyHash={}",
                    SafeLogFields.owner(effectiveCommand.ownerId()),
                    effectiveCommand.agentId(),
                    tool.id(),
                    sandboxWorkload.get(),
                    SafeLogFields.session(effectiveCommand.sessionId()),
                    SafeLogFields.idempotency(effectiveCommand.idempotencyKey()));
            recordActivity(effectiveCommand, tool, result, startedAt, result.message());
            recordToolTelemetry(effectiveCommand, tool.name(), result, startedAt);
            return result;
        }

        if (tool.riskLevel() == ToolRiskLevel.HIGH_RISK) {
            ToolExecutionResult approvalRejection = approvalRejection(effectiveCommand, tool, parameterFingerprint);
            if (approvalRejection != null) {
                recordActivity(effectiveCommand, tool, approvalRejection, startedAt, approvalRejection.message());
                recordToolTelemetry(effectiveCommand, tool.name(), approvalRejection, startedAt);
                return approvalRejection;
            }
        }
        if (tool.riskLevel() == ToolRiskLevel.HIGH_RISK && !approvalRequested(effectiveCommand)) {
            // High-risk operations stop here until confirmed; idempotency and execution are intentionally later.
            ToolPendingConfirmation pending = markPendingConfirmation(
                    effectiveCommand,
                    tool,
                    parameterFingerprint,
                    Map.of(
                            "toolName", tool.name(),
                            "riskLevel", tool.riskLevel().name(),
                            "parameters", sanitizeInput(tool, effectiveCommand.parameters())));
            ToolExecutionResult result = ToolExecutionResult.pending(tool.id(), pending.operationSummary());
            log.info(
                    "tool high_risk pending ownerHash={} agentId={} toolId={} sessionHash={} idempotencyHash={}",
                    SafeLogFields.owner(effectiveCommand.ownerId()),
                    effectiveCommand.agentId(),
                    tool.id(),
                    SafeLogFields.session(effectiveCommand.sessionId()),
                    SafeLogFields.idempotency(effectiveCommand.idempotencyKey()));
            recordActivity(effectiveCommand, tool, result, startedAt, result.message());
            recordToolTelemetry(effectiveCommand, tool.name(), result, startedAt);
            return result;
        }

        // Idempotency is checked immediately before execution and saved only after a real executor result.
        if (idempotencyKey != null) {
            Optional<ToolIdempotencyRecord> previous = store.findIdempotentResult(idempotencyKey);
            if (previous.isPresent()) {
                if (!previous.get().parameterFingerprint().equals(parameterFingerprint)) {
                    ToolExecutionResult conflict = ToolExecutionResult.idempotencyConflict(tool.id());
                    log.warn(
                            "tool idempotency conflict ownerHash={} agentId={} toolId={} sessionHash={} idempotencyHash={}",
                            SafeLogFields.owner(effectiveCommand.ownerId()),
                            effectiveCommand.agentId(),
                            tool.id(),
                            SafeLogFields.session(effectiveCommand.sessionId()),
                            SafeLogFields.idempotency(effectiveCommand.idempotencyKey()));
                    recordActivity(effectiveCommand, tool, conflict, startedAt, conflict.message());
                    recordToolTelemetry(effectiveCommand, tool.name(), conflict, startedAt);
                    return conflict;
                }
                ToolExecutionResult duplicate = ToolExecutionResult.duplicate(tool.id(), previous.get().result());
                log.info(
                        "tool idempotency reused ownerHash={} agentId={} toolId={} sessionHash={} idempotencyHash={}",
                        SafeLogFields.owner(effectiveCommand.ownerId()),
                        effectiveCommand.agentId(),
                        tool.id(),
                        SafeLogFields.session(effectiveCommand.sessionId()),
                        SafeLogFields.idempotency(effectiveCommand.idempotencyKey()));
                recordActivity(effectiveCommand, tool, duplicate, startedAt, duplicate.message());
                recordToolTelemetry(effectiveCommand, tool.name(), duplicate, startedAt);
                return duplicate;
            }
        }

        ToolExecutionResult result = runExecutor(tool, effectiveCommand);
        if (idempotencyKey != null && result.status() != ToolExecutionStatus.DUPLICATE) {
            store.saveIdempotentResult(idempotencyKey, parameterFingerprint, result);
        }
        if (result.status() == ToolExecutionStatus.FAILED) {
            log.warn(
                    "tool execution failed ownerHash={} agentId={} toolId={} sessionHash={} reason={}",
                    SafeLogFields.owner(effectiveCommand.ownerId()),
                    effectiveCommand.agentId(),
                    tool.id(),
                    SafeLogFields.session(effectiveCommand.sessionId()),
                    SafeLogFields.reasonCode(result.message()));
        }
        recordActivity(effectiveCommand, tool, result, startedAt, result.status() == ToolExecutionStatus.FAILED ? result.message() : "");
        recordToolTelemetry(effectiveCommand, tool.name(), result, startedAt);
        return result;
    }

    public ToolExecutionResult reject(ToolExecutionCommand command) {
        Instant startedAt = Instant.now();
        Optional<ToolDefinition> found = store.findTool(command.toolId());
        if (found.isEmpty()) {
            ToolExecutionResult result = ToolExecutionResult.denied(command.toolId(), "Unknown tool.");
            log.warn(
                    "tool reject unknown ownerHash={} agentId={} toolId={} sessionHash={}",
                    SafeLogFields.owner(command.ownerId()),
                    command.agentId(),
                    command.toolId(),
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
        String rejectionReason = rejected.message();
        latestPendingConfirmation(command, tool)
                .ifPresent(pending -> store.savePendingConfirmation(pending.rejected(rejectionReason)));
        recordActivity(command, tool, rejected, startedAt, rejected.message());
        recordToolTelemetry(command, tool.name(), rejected, startedAt);
        return rejected;
    }

    public ToolExecutionResult resumeConfirmation(
            String confirmationId,
            ToolConfirmationAction action,
            ToolExecutionCommand request) {
        Optional<ToolPendingConfirmation> foundPending = store.findPendingConfirmation(confirmationId);
        if (foundPending.isEmpty()) {
            return ToolExecutionResult.denied("", "No matching pending confirmation for this tool call.");
        }
        ToolPendingConfirmation pending = foundPending.get();
        if (pending.status() != ToolPendingConfirmationStatus.PENDING) {
            return ToolExecutionResult.denied(pending.toolId(), "Pending confirmation is not active.");
        }
        if (!matchesPendingContext(pending, request)) {
            return ToolExecutionResult.denied(pending.toolId(), "Pending confirmation does not match this caller context.");
        }
        Optional<ToolDefinition> foundTool = store.findTool(pending.toolId());
        if (foundTool.isEmpty()) {
            return ToolExecutionResult.denied(pending.toolId(), "Unknown tool.");
        }
        ToolConfirmationAction effectiveAction = action == null ? ToolConfirmationAction.CONFIRM : action;
        Map<String, Object> parameters = request.parameters() == null || request.parameters().isEmpty()
                ? pending.parameters()
                : request.parameters();
        ToolExecutionCommand command = new ToolExecutionCommand(
                pending.ownerScopeId(),
                pending.ownerId(),
                pending.agentId(),
                pending.sessionId(),
                pending.toolId(),
                parameters,
                true,
                pending.idempotencyKey());
        if (effectiveAction == ToolConfirmationAction.REJECT) {
            return reject(command);
        }
        return execute(command);
    }

    public List<ToolActivityRecord> listActivity(String ownerScopeId) {
        return store.listActivity(ownerScopeId);
    }

    public List<ToolPendingConfirmation> listPendingConfirmations(
            String ownerScopeId,
            String ownerId,
            String agentId,
            String sessionId) {
        return store.listPendingConfirmations(ownerScopeId, ownerId, agentId, sessionId);
    }

    private static boolean matchesPendingContext(ToolPendingConfirmation pending, ToolExecutionCommand request) {
        return pending.ownerScopeId().equals(request.ownerScopeId())
                && pending.ownerId().equals(request.ownerId())
                && pending.agentId().equals(request.agentId())
                && pending.sessionId().equals(request.sessionId());
    }

    private ToolExecutionResult preflight(ToolExecutionCommand command, ToolDefinition tool) {
        if (!tool.enabled()) {
            return ToolExecutionResult.denied(tool.id(), "Tool is disabled.");
        }
        if (!tool.ownerScopeId().equals(command.ownerScopeId())) {
            return ToolExecutionResult.denied(tool.id(), "Tool does not belong to this owner scope.");
        }
        if (!tool.permissionPolicy().permits(command.principal())) {
            return ToolExecutionResult.denied(tool.id(), "Owner or Agent is not allowed to use this tool.");
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
        Optional<String> workspacePathError = validateWorkspacePaths(command, tool);
        if (workspacePathError.isPresent()) {
            return ToolExecutionResult.denied(tool.id(), workspacePathError.get());
        }
        if (tool.mutating() && command.idempotencyKey() == null) {
            return ToolExecutionResult.denied(tool.id(), "Idempotency key is required for mutating tools.");
        }
        return null;
    }

    private Optional<String> validateWorkspacePaths(ToolExecutionCommand command, ToolDefinition tool) {
        java.util.LinkedHashSet<String> pathParameters =
                new java.util.LinkedHashSet<>(tool.parameterSchema().workspacePathParameters());
        tool.parameterSchema().allowedParameters().stream()
                .filter(ToolService::looksLikeWorkspacePathParameter)
                .forEach(pathParameters::add);
        if (pathParameters.isEmpty()) {
            return Optional.empty();
        }
        RuntimeContextScope context = RuntimeContextScope.fromOwnerScope(
                new OwnerScope(command.ownerId(), command.agentId(), command.sessionId()));
        for (String parameter : pathParameters) {
            Object value = command.parameters().get(parameter);
            Optional<String> error = validateWorkspacePathValue(context, value);
            if (error.isPresent()) {
                return error;
            }
        }
        return Optional.empty();
    }

    private Optional<String> validateWorkspacePathValue(RuntimeContextScope context, Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Optional<String> error = validateWorkspacePathValue(context, item);
                if (error.isPresent()) {
                    return error;
                }
            }
            return Optional.empty();
        }
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            return Optional.empty();
        }
        try {
            personalWorkspaceService.resolveAuthorizedPath(context, stringValue);
            return Optional.empty();
        } catch (IllegalArgumentException ex) {
            return Optional.of(ex.getMessage());
        }
    }

    private static boolean looksLikeWorkspacePathParameter(String name) {
        if (name == null) {
            return false;
        }
        String normalized = name.toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("path")
                || normalized.endsWith("path")
                || normalized.equals("workspaceuri")
                || normalized.equals("workspace_uri");
    }

    private ToolExecutionResult planModeRejection(ToolExecutionCommand command, ToolDefinition tool) {
        if (tool.mutating()
                || tool.riskLevel() == ToolRiskLevel.HIGH_RISK
                || sandboxWorkload(tool).isPresent()) {
            return ToolExecutionResult.denied(
                    tool.id(),
                    "Plan mode is read-only and cannot execute side-effect or sandboxed tools.");
        }
        return null;
    }

    private ToolExecutionResult runExecutor(ToolDefinition tool, ToolExecutionCommand command) {
        try {
            Optional<AgentWorkloadType> sandboxWorkload = sandboxWorkload(tool);
            if (sandboxWorkload.isPresent()) {
                return runSandboxedExecutor(tool, command, sandboxWorkload.get());
            }
            ToolExecutor executor = executors.stream()
                    .filter(candidate -> candidate.supports(tool))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No executor supports tool: " + tool.name()));
            Map<String, Object> output = timeoutGuard.guardTool(Mono.fromCallable(
                    () -> executor.execute(tool, command.parameters(), command))).block();
            return ToolExecutionResult.success(tool.id(), output);
        } catch (RuntimeException exception) {
            return ToolExecutionResult.failed(tool.id(), exception.getMessage());
        }
    }

    private ToolExecutionResult runSandboxedExecutor(
            ToolDefinition tool,
            ToolExecutionCommand command,
            AgentWorkloadType workloadType) {
        RuntimeContextScope context = RuntimeContextScope.fromOwnerScope(
                new OwnerScope(command.ownerId(), command.agentId(), command.sessionId()));
        PersonalWorkspaceLayout layout = personalWorkspaceService.initialize(context);
        SandboxExecutionPolicy policy = sandboxPolicyService.policyFor(
                context,
                workloadType,
                layout.root());
        SandboxExecutor sandboxExecutor = sandboxExecutorRegistry.executor(policy.mode());
        SandboxExecutionRequest request = new SandboxExecutionRequest(
                context,
                workloadType,
                sandboxCommand(tool, command.parameters()),
                sandboxArguments(command.parameters()),
                layout.root(),
                sandboxEnvironment(command.parameters()),
                command.idempotencyKey());
        SandboxExecutionResult sandboxResult = timeoutGuard.guardSandbox(Mono.fromCallable(
                () -> sandboxExecutor.execute(policy, request))).block();
        if (sandboxResult.status() == SandboxExecutionStatus.SUCCEEDED) {
            return ToolExecutionResult.success(tool.id(), sandboxOutput(policy, sandboxResult));
        }
        if (sandboxResult.status() == SandboxExecutionStatus.REJECTED) {
            return ToolExecutionResult.denied(tool.id(), sandboxResult.message());
        }
        return ToolExecutionResult.failed(tool.id(), sandboxResult.message());
    }

    private static Optional<AgentWorkloadType> sandboxWorkload(ToolDefinition tool) {
        if (tool.workloadType() != AgentWorkloadType.OFFICE) {
            return Optional.of(tool.workloadType());
        }
        if (tool.parameterSchema().allowedParameters().stream()
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(EXECUTION_PARAMETER_NAMES::contains)) {
            return Optional.of(AgentWorkloadType.UNTRUSTED);
        }
        String normalized = String.join(" ",
                tool.name(),
                tool.description(),
                tool.ownerSystem(),
                tool.sourceRef()).toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("untrusted")) {
            return Optional.of(AgentWorkloadType.UNTRUSTED);
        }
        if (normalized.contains("shell")
                || normalized.contains("bash")
                || normalized.contains("terminal")
                || normalized.contains("command")) {
            return Optional.of(AgentWorkloadType.SHELL);
        }
        if (normalized.contains("sql")
                || normalized.contains("database")) {
            return Optional.of(AgentWorkloadType.SQL);
        }
        if (normalized.contains("code")
                || normalized.contains("script")
                || normalized.contains("python")
                || normalized.contains("node")
                || normalized.contains("java")) {
            return Optional.of(AgentWorkloadType.CODE);
        }
        return Optional.empty();
    }

    private static String sandboxCommand(ToolDefinition tool, Map<String, Object> parameters) {
        for (String key : List.of("command", "script", "code", "sql", "query")) {
            Object value = parameters.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return tool.name();
    }

    private static List<String> sandboxArguments(Map<String, Object> parameters) {
        Object raw = parameters.containsKey("arguments") ? parameters.get("arguments") : parameters.get("args");
        if (raw instanceof Iterable<?> iterable) {
            java.util.ArrayList<String> values = new java.util.ArrayList<>();
            iterable.forEach(value -> {
                if (value != null) {
                    values.add(String.valueOf(value));
                }
            });
            return List.copyOf(values);
        }
        if (raw != null && !String.valueOf(raw).isBlank()) {
            return List.of(String.valueOf(raw));
        }
        return List.of();
    }

    private static Map<String, String> sandboxEnvironment(Map<String, Object> parameters) {
        Object raw = parameters.get("environment");
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> environment = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key != null && !String.valueOf(key).isBlank()) {
                environment.put(String.valueOf(key), value == null ? "" : String.valueOf(value));
            }
        });
        return Map.copyOf(environment);
    }

    private static Map<String, Object> sandboxOutput(
            SandboxExecutionPolicy policy,
            SandboxExecutionResult result) {
        Map<String, Object> sandbox = new LinkedHashMap<>();
        sandbox.put("mode", policy.mode().name());
        sandbox.put("workspaceRoot", policy.workspaceRoot().toString());
        sandbox.put("exitCode", result.exitCode());
        sandbox.put("status", result.status().name());
        if (policy.mode() == SandboxExecutionMode.DOCKER) {
            sandbox.put("image", policy.image());
        }
        if (policy.mode() == SandboxExecutionMode.REMOTE) {
            sandbox.put("remoteEndpoint", policy.remoteEndpoint());
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("sandbox", Map.copyOf(sandbox));
        output.put("stdout", result.stdout());
        output.put("stderr", result.stderr());
        output.put("metadata", result.metadata());
        return Map.copyOf(output);
    }

    private ToolExecutionResult approvalRejection(
            ToolExecutionCommand command,
            ToolDefinition tool,
            String parameterFingerprint) {
        if (!approvalRequested(command)) {
            return null;
        }
        Optional<ToolPendingConfirmation> pending = latestPendingConfirmation(command, tool);
        if (pending.isEmpty()) {
            return ToolExecutionResult.denied(tool.id(), "No matching pending confirmation for this tool call.");
        }
        if (!pending.get().parameterFingerprint().equals(parameterFingerprint)) {
            store.savePendingConfirmation(pending.get().rejected("superseded by modified parameters"));
            ToolPendingConfirmation modified = markPendingConfirmation(
                    command,
                    tool,
                    parameterFingerprint,
                    Map.of(
                            "toolName", tool.name(),
                            "riskLevel", tool.riskLevel().name(),
                            "modifiedParameters", true,
                            "previousConfirmationId", pending.get().confirmationId(),
                            "parameters", sanitizeInput(tool, command.parameters())));
            return ToolExecutionResult.pending(tool.id(), modified.operationSummary());
        }
        boolean claimed = store.claimPendingConfirmation(pending.get().confirmationId(), "confirmed");
        if (!claimed) {
            return ToolExecutionResult.denied(tool.id(), "Pending confirmation is not active.");
        }
        return null;
    }

    private ToolPendingConfirmation markPendingConfirmation(
            ToolExecutionCommand command,
            ToolDefinition tool,
            String parameterFingerprint,
            Map<String, Object> operationSummary) {
        ToolPendingConfirmation pending = ToolPendingConfirmation.pending(
                command.ownerScopeId(),
                command.ownerId(),
                command.agentId(),
                command.sessionId(),
                tool,
                command.parameters(),
                sanitizeInput(tool, command.parameters()),
                operationSummary,
                parameterFingerprint,
                command.idempotencyKey());
        Map<String, Object> summary = new LinkedHashMap<>(operationSummary);
        summary.put("confirmationId", pending.confirmationId());
        summary.put("status", ToolPendingConfirmationStatus.PENDING.name());
        ToolPendingConfirmation withSummary = pending.withOperationSummary(summary);
        return store.savePendingConfirmation(withSummary);
    }

    private static boolean approvalRequested(ToolExecutionCommand command) {
        return command.confirmed();
    }

    private Optional<ToolPendingConfirmation> latestPendingConfirmation(ToolExecutionCommand command, ToolDefinition tool) {
        return store.listPendingConfirmations(
                        command.ownerScopeId(),
                        command.ownerId(),
                        command.agentId(),
                        command.sessionId()).stream()
                .filter(pending -> pending.toolId().equals(tool.id()))
                .reduce((first, second) -> second);
    }

    private String idempotencyKey(ToolDefinition tool, ToolExecutionCommand command) {
        if (!tool.mutating()) {
            return null;
        }
        return command.ownerScopeId()
                + ":" + command.ownerId()
                + ":" + command.agentId()
                + ":" + command.sessionId()
                + ":" + tool.id()
                + ":" + command.idempotencyKey();
    }

    private void auditUnknown(ToolExecutionCommand command, ToolExecutionResult result, Instant startedAt) {
        store.saveActivity(new ToolActivityRecord(
                null,
                Instant.now(),
                command.ownerScopeId(),
                command.ownerId(),
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
                null,
                null,
                command.idempotencyKey(),
                result.message()));
    }

    private void recordActivity(
            ToolExecutionCommand command,
            ToolDefinition tool,
            ToolExecutionResult result,
            Instant startedAt,
            String failureReason) {
        if (!tool.activityPolicy().enabled()) {
            return;
        }
        store.saveActivity(new ToolActivityRecord(
                null,
                Instant.now(),
                command.ownerScopeId(),
                command.ownerId(),
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
                null,
                null,
                command.idempotencyKey(),
                failureReason));
    }

    private Map<String, Object> sanitizeInput(ToolDefinition tool, Map<String, Object> input) {
        Set<String> sensitive = union(
                tool.parameterSchema().sensitiveParameters(),
                tool.activityPolicy().sensitiveParameters());
        return sanitize(input, sensitive);
    }

    private Map<String, Object> sanitizeOutput(ToolDefinition tool, Map<String, Object> output) {
        Set<String> sensitive = union(
                union(tool.parameterSchema().sensitiveParameters(), tool.activityPolicy().sensitiveParameters()),
                tool.activityPolicy().sensitiveResultFields());
        return sanitizeOutputValue(sanitize(output, sensitive));
    }

    private static Map<String, Object> sanitizeOutputValue(Map<String, Object> output) {
        if (output.isEmpty()) {
            return output;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        output.forEach((key, value) -> {
            if ("stdout".equalsIgnoreCase(key) || "stderr".equalsIgnoreCase(key)) {
                sanitized.put(key, textSummary(value));
            } else {
                sanitized.put(key, value);
            }
        });
        return Map.copyOf(sanitized);
    }

    private static Map<String, Object> textSummary(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return Map.of(
                "size", text.length(),
                "sha256", sha256(text));
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

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available.", ex);
        }
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
                command.ownerScopeId(),
                command.ownerId(),
                command.agentId(),
                "tool-service",
                Duration.between(startedAt, Instant.now()),
                Map.of(
                        "toolName", toolName,
                        "status", result.status().name(),
                        "approvalRequired", result.approvalRequired()));
    }
}
