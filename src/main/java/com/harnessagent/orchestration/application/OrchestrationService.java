package com.harnessagent.orchestration.application;

import com.harnessagent.agent.runtime.AgentReply;
import com.harnessagent.agent.runtime.AgentRunRequest;
import com.harnessagent.agent.runtime.AgentRuntime;
import com.harnessagent.production.telemetry.RuntimeTelemetry;
import com.harnessagent.production.telemetry.TelemetryEventType;
import com.harnessagent.security.domain.OwnerPrincipal;
import com.harnessagent.security.application.SafeLogFields;
import com.harnessagent.security.application.SensitiveDataRedactor;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.tooling.domain.ToolActivityPolicy;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolOutputSchema;
import com.harnessagent.tooling.domain.ToolParameterSchema;
import com.harnessagent.tooling.domain.ToolPermissionPolicy;
import com.harnessagent.tooling.domain.ToolRegistration;
import com.harnessagent.tooling.domain.ToolRiskLevel;
import com.harnessagent.tooling.application.ToolService;
import com.harnessagent.tooling.domain.ToolSourceType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.harnessagent.orchestration.domain.AgentToolDefinition;
import com.harnessagent.orchestration.domain.DelegationMode;
import com.harnessagent.orchestration.domain.ExpertAgentDefinition;
import com.harnessagent.orchestration.domain.FailureStrategy;
import com.harnessagent.orchestration.domain.HandoffRecord;
import com.harnessagent.orchestration.domain.OrchestrationRequest;
import com.harnessagent.orchestration.domain.OrchestrationResult;
import com.harnessagent.orchestration.domain.OrchestrationStatus;
import com.harnessagent.orchestration.domain.OrchestrationStep;
import com.harnessagent.orchestration.domain.OrchestrationTrace;
import com.harnessagent.orchestration.domain.RouteDecision;

@Service
public class OrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);

    private final ExpertAgentRegistry registry;
    private final SupervisorRouter router;
    private final AgentRuntime agentRuntime;
    private final RuntimeContextFactory contextFactory;
    private final ToolService toolService;
    private final RuntimeTelemetry telemetry;
    private final SensitiveDataRedactor redactor;
    private final OrchestrationTraceStore traceStore;

    public OrchestrationService(
            ExpertAgentRegistry registry,
            SupervisorRouter router,
            AgentRuntime agentRuntime,
            RuntimeContextFactory contextFactory,
            ToolService toolService,
            RuntimeTelemetry telemetry,
            SensitiveDataRedactor redactor) {
        this(
                registry,
                router,
                agentRuntime,
                contextFactory,
                toolService,
                telemetry,
                redactor,
                new OrchestrationTraceStore());
    }

    @Autowired
    public OrchestrationService(
            ExpertAgentRegistry registry,
            SupervisorRouter router,
            AgentRuntime agentRuntime,
            RuntimeContextFactory contextFactory,
            ToolService toolService,
            RuntimeTelemetry telemetry,
            SensitiveDataRedactor redactor,
            OrchestrationTraceStore traceStore) {
        this.registry = registry;
        this.router = router;
        this.agentRuntime = agentRuntime;
        this.contextFactory = contextFactory;
        this.toolService = toolService;
        this.telemetry = telemetry;
        this.redactor = redactor;
        this.traceStore = traceStore == null ? new OrchestrationTraceStore() : traceStore;
    }

    public ExpertAgentDefinition register(ExpertAgentDefinition definition) {
        return registry.register(definition);
    }

    public AgentToolDefinition asTool(String childAgentId) {
        ToolDefinition ignored = registerAgentAsTool(childAgentId);
        return agentToolDefinition(childAgentId);
    }

    public ToolDefinition registerAgentAsTool(String childAgentId) {
        ExpertAgentDefinition child = registry.find(childAgentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown child agent: " + childAgentId));
        if (!child.approved()) {
            throw new IllegalStateException("Child agent must be approved before exposing as tool.");
        }
        boolean mutating = childMayMutate(child);
        return toolService.registerTool(new ToolRegistration(
                child.ownerScopeId(),
                "agent." + child.name(),
                child.purpose(),
                "agent-orchestration",
                child.ownerId(),
                ToolSourceType.AGENT,
                child.id(),
                mutating ? ToolRiskLevel.HIGH_RISK : ToolRiskLevel.READ_ONLY,
                mutating,
                child.enabled(),
                new ToolParameterSchema(Set.of("task"), Set.of("context"), Map.of(), Set.of()),
                ToolOutputSchema.structured("application/json", Map.of("type", "object", "format", "agent-tool-result")),
                new ToolPermissionPolicy(Set.of(child.ownerId()), Set.of(), Set.of()),
                ToolActivityPolicy.standard()));
    }

    private AgentToolDefinition agentToolDefinition(String childAgentId) {
        ExpertAgentDefinition child = registry.find(childAgentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown child agent: " + childAgentId));
        return new AgentToolDefinition(
                "agent." + child.name(),
                child.id(),
                child.inputContract(),
                child.outputContract(),
                child.allowedOwnerIds());
    }

    public OrchestrationResult orchestrate(OrchestrationRequest request) {
        Instant startedAt = Instant.now();
        List<ExpertAgentDefinition> candidates = registry.list(request.principal().scopeId());
        RouteDecision decision = router.route(request.principal(), request.taskIntent(), request.context(), candidates);
        log.info(
                "orchestration route ownerHash={} supervisorAgentId={} selectedAgentId={} status={} candidateCount={}",
                SafeLogFields.owner(request.principal().ownerId()),
                request.supervisorAgentId(),
                decision.selectedAgentId(),
                decision.status(),
                candidates.size());
        List<OrchestrationStep> steps = new ArrayList<>();
        List<HandoffRecord> handoffs = new ArrayList<>();
        Map<String, Object> attributes = new LinkedHashMap<>();
        Map<String, Object> candidateReasons = candidateReasons(request.principal(), request.taskIntent(), candidates);
        attributes.put("reason", decision.reason());
        attributes.put("candidateReasons", candidateReasons);
        attributes.put("delegationMode", request.delegationMode().name());
        attributes.put("failureStrategy", request.failureStrategy().name());
        steps.add(new OrchestrationStep(
                null,
                request.supervisorAgentId(),
                "route",
                Map.of("taskIntent", safeString(request.taskIntent()), "candidateAgentIds", candidateReasons.keySet()),
                Map.of(
                        "selectedAgentId", safeString(decision.selectedAgentId()),
                        "reason", safeString(decision.reason()),
                        "confidence", decision.confidence()),
                decision.status()));
        OrchestrationStatus status = decision.status();
        ExpertAgentDefinition backgroundSelected = null;
        Map<String, Object> backgroundSharedContext = Map.of();
        if (decision.status() == OrchestrationStatus.ROUTED) {
            ExpertAgentDefinition selected = registry.find(decision.selectedAgentId())
                    .orElseThrow(() -> new IllegalStateException("Selected agent is not registered."));
            Map<String, Object> sharedContext = redactor.redactMap(selected.contextBoundary().filter(request.context()));
            handoffs.add(new HandoffRecord(
                    Instant.now(),
                    request.supervisorAgentId(),
                    selected.id(),
                    "supervisor_route",
                    sharedContext));
            steps.add(new OrchestrationStep(
                    null,
                    selected.id(),
                    "handoff",
                    Map.of("fromAgentId", request.supervisorAgentId(), "context", sharedContext),
                    Map.of("toAgentId", selected.id()),
                    OrchestrationStatus.HANDOFF));
            status = executeSelectedAgent(request, selected, sharedContext, steps, attributes);
            if (status == OrchestrationStatus.BACKGROUND_RUNNING) {
                backgroundSelected = selected;
                backgroundSharedContext = sharedContext;
            }
            log.info(
                    "orchestration handoff executed ownerHash={} supervisorAgentId={} selectedAgentId={}",
                    SafeLogFields.owner(request.principal().ownerId()),
                    request.supervisorAgentId(),
                    selected.id());
        } else {
            attributes.put("nextAction", nextAction(request.failureStrategy()));
            log.warn(
                    "orchestration route not_executed ownerHash={} supervisorAgentId={} status={} reason={}",
                    SafeLogFields.owner(request.principal().ownerId()),
                    request.supervisorAgentId(),
                    decision.status(),
                    SafeLogFields.reasonCode(decision.reason()));
        }
        OrchestrationTrace trace = new OrchestrationTrace(
                null,
                Instant.now(),
                request.principal().scopeId(),
                request.principal().ownerId(),
                request.supervisorAgentId(),
                decision.selectedAgentId(),
                request.taskIntent(),
                decision.confidence(),
                status,
                candidates.stream().map(ExpertAgentDefinition::id).toList(),
                steps,
                handoffs,
                attributes);
        traceStore.save(trace);
        if (status == OrchestrationStatus.BACKGROUND_RUNNING && backgroundSelected != null) {
            scheduleBackgroundCompletion(request, backgroundSelected, backgroundSharedContext, trace.id());
        }
        telemetry.record(
                TelemetryEventType.ORCHESTRATION,
                request.principal().scopeId(),
                request.principal().ownerId(),
                request.supervisorAgentId(),
                "orchestration",
                Duration.between(startedAt, Instant.now()),
                Map.of("status", trace.status().name(), "confidence", trace.confidence()));
        return new OrchestrationResult(decision, trace);
    }

    private OrchestrationStatus executeSelectedAgent(
            OrchestrationRequest request,
            ExpertAgentDefinition selected,
            Map<String, Object> sharedContext,
            List<OrchestrationStep> steps,
            Map<String, Object> attributes) {
        if (request.delegationMode() == DelegationMode.BACKGROUND) {
            String runId = "bg-" + UUID.randomUUID();
            steps.add(new OrchestrationStep(
                    null,
                    selected.id(),
                    "background_delegate",
                    Map.of("task", safeString(request.task()), "context", sharedContext),
                    Map.of("runId", runId),
                    OrchestrationStatus.BACKGROUND_RUNNING));
            attributes.put("backgroundRunId", runId);
            attributes.put("systemReminder", "Background subagent " + selected.name() + " accepted.");
            return OrchestrationStatus.BACKGROUND_RUNNING;
        }
        try {
            String result = invokeAgent(request.principal(), selected.id(), safeString(request.task()), sharedContext);
            steps.add(new OrchestrationStep(
                    null,
                    selected.id(),
                    "execute_task",
                    Map.of("task", safeString(request.task()), "context", sharedContext),
                    Map.of("result", result),
                    OrchestrationStatus.EXECUTED));
            steps.add(new OrchestrationStep(
                    null,
                    request.supervisorAgentId(),
                    "assemble_result",
                    Map.of("childAgentId", selected.id()),
                    Map.of("result", result),
                    OrchestrationStatus.EXECUTED));
            attributes.put("result", result);
            return OrchestrationStatus.EXECUTED;
        } catch (RuntimeException ex) {
            steps.add(new OrchestrationStep(
                    null,
                    selected.id(),
                    "execute_task_failed",
                    Map.of("task", safeString(request.task())),
                    Map.of("errorType", ex.getClass().getSimpleName(), "message", safeString(ex.getMessage())),
                    OrchestrationStatus.BLOCKED));
            return handleFailure(request, selected, sharedContext, steps, attributes, ex);
        }
    }

    private OrchestrationStatus handleFailure(
            OrchestrationRequest request,
            ExpertAgentDefinition selected,
            Map<String, Object> sharedContext,
            List<OrchestrationStep> steps,
            Map<String, Object> attributes,
            RuntimeException ex) {
        if (request.failureStrategy() == FailureStrategy.FALLBACK_TO_SUPERVISOR) {
            try {
                String result = invokeAgent(
                        request.principal(),
                        request.supervisorAgentId(),
                        safeString(request.task()),
                        sharedContext);
                steps.add(new OrchestrationStep(
                        null,
                        request.supervisorAgentId(),
                        "fallback_to_supervisor",
                        Map.of("failedAgentId", selected.id(), "errorType", ex.getClass().getSimpleName()),
                        Map.of("result", result),
                        OrchestrationStatus.EXECUTED));
                steps.add(new OrchestrationStep(
                        null,
                        request.supervisorAgentId(),
                        "assemble_result",
                        Map.of("childAgentId", selected.id(), "fallback", true),
                        Map.of("result", result),
                        OrchestrationStatus.EXECUTED));
                attributes.put("fallbackAgentId", request.supervisorAgentId());
                attributes.put("result", result);
                return OrchestrationStatus.EXECUTED;
            } catch (RuntimeException fallbackException) {
                steps.add(new OrchestrationStep(
                        null,
                        request.supervisorAgentId(),
                        "fallback_to_supervisor_failed",
                        Map.of("failedAgentId", selected.id(), "errorType", ex.getClass().getSimpleName()),
                        Map.of(
                                "errorType", fallbackException.getClass().getSimpleName(),
                                "message", safeString(fallbackException.getMessage())),
                        OrchestrationStatus.BLOCKED));
                attributes.put("errorType", fallbackException.getClass().getSimpleName());
                attributes.put("nextAction", "stop");
                return OrchestrationStatus.BLOCKED;
            }
        }
        if (request.failureStrategy() == FailureStrategy.RETRY) {
            try {
                String result = invokeAgent(request.principal(), selected.id(), safeString(request.task()), sharedContext);
                steps.add(new OrchestrationStep(
                        null,
                        selected.id(),
                        "retry_execute_task",
                        Map.of("task", safeString(request.task())),
                        Map.of("result", result),
                        OrchestrationStatus.EXECUTED));
                attributes.put("result", result);
                return OrchestrationStatus.EXECUTED;
            } catch (RuntimeException retryException) {
                steps.add(new OrchestrationStep(
                        null,
                        selected.id(),
                        "retry_execute_task_failed",
                        Map.of("task", safeString(request.task())),
                        Map.of(
                                "errorType", retryException.getClass().getSimpleName(),
                                "message", safeString(retryException.getMessage())),
                        OrchestrationStatus.BLOCKED));
                attributes.put("errorType", retryException.getClass().getSimpleName());
                attributes.put("nextAction", "stop");
                return OrchestrationStatus.BLOCKED;
            }
        }
        attributes.put("errorType", ex.getClass().getSimpleName());
        attributes.put("nextAction", nextAction(request.failureStrategy()));
        return request.failureStrategy() == FailureStrategy.CLARIFY
                ? OrchestrationStatus.ESCALATED
                : OrchestrationStatus.BLOCKED;
    }

    private void scheduleBackgroundCompletion(
            OrchestrationRequest request,
            ExpertAgentDefinition selected,
            Map<String, Object> sharedContext,
            String parentTraceId) {
        CompletableFuture.runAsync(() -> {
            try {
                String result = invokeAgent(request.principal(), selected.id(), safeString(request.task()), sharedContext);
                traceStore.save(new OrchestrationTrace(
                        null,
                        Instant.now(),
                        request.principal().scopeId(),
                        request.principal().ownerId(),
                        request.supervisorAgentId(),
                        selected.id(),
                        request.taskIntent(),
                        1d,
                        OrchestrationStatus.BACKGROUND_COMPLETED,
                        List.of(selected.id()),
                        List.of(new OrchestrationStep(
                                null,
                                selected.id(),
                                "background_complete",
                                Map.of("task", safeString(request.task())),
                                Map.of("result", result),
                                OrchestrationStatus.BACKGROUND_COMPLETED)),
                        List.of(new HandoffRecord(
                                Instant.now(),
                                request.supervisorAgentId(),
                                selected.id(),
                                "background_delegate",
                                sharedContext)),
                        Map.of(
                                "parentTraceId", parentTraceId,
                                "systemReminder", "Background subagent " + selected.name() + " completed.",
                                "result", result)));
            } catch (RuntimeException ex) {
                traceStore.save(new OrchestrationTrace(
                        null,
                        Instant.now(),
                        request.principal().scopeId(),
                        request.principal().ownerId(),
                        request.supervisorAgentId(),
                        selected.id(),
                        request.taskIntent(),
                        1d,
                        OrchestrationStatus.BLOCKED,
                        List.of(selected.id()),
                        List.of(new OrchestrationStep(
                                null,
                                selected.id(),
                                "background_failed",
                                Map.of("task", safeString(request.task())),
                                Map.of(
                                        "errorType", ex.getClass().getSimpleName(),
                                        "message", safeString(ex.getMessage())),
                                OrchestrationStatus.BLOCKED)),
                        List.of(new HandoffRecord(
                                Instant.now(),
                                request.supervisorAgentId(),
                                selected.id(),
                                "background_delegate",
                                sharedContext)),
                        Map.of(
                                "parentTraceId", parentTraceId,
                                "errorType", ex.getClass().getSimpleName(),
                                "nextAction", "stop")));
            }
        });
    }

    private String invokeAgent(
            OwnerPrincipal principal,
            String agentId,
            String task,
            Map<String, Object> sharedContext) {
        RuntimeContextScope childContext = contextFactory.createFromOwnerScope(
                principal.scopeId(),
                principal.ownerId(),
                agentId,
                "handoff-" + UUID.randomUUID());
        AgentReply reply = agentRuntime.complete(new AgentRunRequest(
                childContext,
                List.of(ChatMessage.user(taskMessage(task, sharedContext))))).block();
        return reply == null ? "" : safeString(reply.content());
    }

    private static boolean childMayMutate(ExpertAgentDefinition child) {
        return child.allowedTools().stream().anyMatch(OrchestrationService::mutatingCapability);
    }

    private static boolean mutatingCapability(String capability) {
        if (capability == null) {
            return false;
        }
        String normalized = capability.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("write")
                || normalized.contains("update")
                || normalized.contains("delete")
                || normalized.contains("create")
                || normalized.contains("send")
                || normalized.contains("shell")
                || normalized.contains("command")
                || normalized.contains("sql")
                || normalized.contains("database")
                || normalized.contains("code")
                || normalized.contains("script");
    }

    private static Map<String, Object> candidateReasons(
            OwnerPrincipal principal,
            String taskIntent,
            List<ExpertAgentDefinition> candidates) {
        Map<String, Object> reasons = new LinkedHashMap<>();
        for (ExpertAgentDefinition candidate : candidates) {
            if (!candidate.approved()) {
                reasons.put(candidate.id(), "not_approved");
            } else if (!candidate.enabled()) {
                reasons.put(candidate.id(), "disabled");
            } else if (!candidate.allowedOwnerIds().isEmpty()
                    && !candidate.allowedOwnerIds().contains(principal.ownerId())) {
                reasons.put(candidate.id(), "owner_policy_not_permitted");
            } else if (candidate.canHandle(taskIntent)) {
                reasons.put(candidate.id(), "matched_intent");
            } else {
                reasons.put(candidate.id(), "low_confidence");
            }
        }
        return Map.copyOf(reasons);
    }

    private static String nextAction(FailureStrategy strategy) {
        return switch (strategy == null ? FailureStrategy.STOP : strategy) {
            case CLARIFY -> "clarify";
            case RETRY -> "retry";
            case FALLBACK_TO_SUPERVISOR -> "fallback_to_supervisor";
            case STOP -> "stop";
        };
    }

    private static String taskMessage(String task, Map<String, Object> sharedContext) {
        if (sharedContext == null || sharedContext.isEmpty()) {
            return task;
        }
        return task + "\n\nShared context: " + sharedContext;
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    public List<OrchestrationTrace> listTraces(OwnerPrincipal principal) {
        return traceStore.list(principal.scopeId());
    }
}
