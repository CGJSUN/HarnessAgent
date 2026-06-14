package com.harnessagent.orchestration;

import com.harnessagent.agent.AgentReply;
import com.harnessagent.agent.AgentRunRequest;
import com.harnessagent.agent.AgentRuntime;
import com.harnessagent.production.RuntimeTelemetry;
import com.harnessagent.production.TelemetryEventType;
import com.harnessagent.security.SecurityPrincipal;
import com.harnessagent.security.SensitiveDataRedactor;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.session.ChatMessage;
import com.harnessagent.tooling.ToolAuditPolicy;
import com.harnessagent.tooling.ToolDefinition;
import com.harnessagent.tooling.ToolParameterSchema;
import com.harnessagent.tooling.ToolPermissionPolicy;
import com.harnessagent.tooling.ToolRegistration;
import com.harnessagent.tooling.ToolRiskLevel;
import com.harnessagent.tooling.ToolService;
import com.harnessagent.tooling.ToolSourceType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class OrchestrationService {

    private final ExpertAgentRegistry registry;
    private final SupervisorRouter router;
    private final AgentRuntime agentRuntime;
    private final RuntimeContextFactory contextFactory;
    private final ToolService toolService;
    private final RuntimeTelemetry telemetry;
    private final SensitiveDataRedactor redactor;
    private final List<OrchestrationTrace> traces = new CopyOnWriteArrayList<>();

    public OrchestrationService(
            ExpertAgentRegistry registry,
            SupervisorRouter router,
            AgentRuntime agentRuntime,
            RuntimeContextFactory contextFactory,
            ToolService toolService,
            RuntimeTelemetry telemetry,
            SensitiveDataRedactor redactor) {
        this.registry = registry;
        this.router = router;
        this.agentRuntime = agentRuntime;
        this.contextFactory = contextFactory;
        this.toolService = toolService;
        this.telemetry = telemetry;
        this.redactor = redactor;
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
        return toolService.registerTool(new ToolRegistration(
                child.tenantId(),
                "agent." + child.name(),
                child.purpose(),
                "agent-orchestration",
                child.ownerId(),
                ToolSourceType.AGENT,
                child.id(),
                ToolRiskLevel.READ_ONLY,
                false,
                child.enabled(),
                new ToolParameterSchema(Set.of("task"), Set.of("context"), Map.of(), Set.of()),
                new ToolPermissionPolicy(Set.of(child.tenantId()), Set.of(), Set.of(), Set.of(), child.requiredRoles()),
                ToolAuditPolicy.standard()));
    }

    private AgentToolDefinition agentToolDefinition(String childAgentId) {
        ExpertAgentDefinition child = registry.find(childAgentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown child agent: " + childAgentId));
        return new AgentToolDefinition(
                "agent." + child.name(),
                child.id(),
                child.inputContract(),
                child.outputContract(),
                child.requiredRoles());
    }

    public OrchestrationResult orchestrate(OrchestrationRequest request) {
        Instant startedAt = Instant.now();
        List<ExpertAgentDefinition> candidates = registry.list(request.principal().tenantId());
        RouteDecision decision = router.route(request.principal(), request.taskIntent(), candidates);
        ContextBoundary boundary = new ContextBoundary(false, false, false, true, java.util.Set.of("question", "citations"));
        Map<String, Object> sharedContext = redactor.redactMap(boundary.filter(request.context()));
        List<OrchestrationStep> steps = new ArrayList<>();
        List<HandoffRecord> handoffs = new ArrayList<>();
        OrchestrationStatus status = decision.status();
        if (decision.status() == OrchestrationStatus.ROUTED) {
            RuntimeContextScope childContext = contextFactory.create(
                    request.principal().tenantId(),
                    request.principal().userId(),
                    decision.selectedAgentId(),
                    "handoff-" + java.util.UUID.randomUUID());
            AgentReply reply = agentRuntime.complete(new AgentRunRequest(
                    childContext,
                    List.of(ChatMessage.user(request.task() == null ? "" : request.task())))).block();
            steps.add(new OrchestrationStep(
                    null,
                    decision.selectedAgentId(),
                    "execute_task",
                    Map.of(
                            "task", request.task() == null ? "" : request.task(),
                            "context", sharedContext),
                    Map.of("result", reply == null ? "" : reply.content()),
                    OrchestrationStatus.EXECUTED));
            handoffs.add(new HandoffRecord(
                    Instant.now(),
                    request.supervisorAgentId(),
                    decision.selectedAgentId(),
                    "supervisor_route",
                    sharedContext));
            status = OrchestrationStatus.EXECUTED;
        }
        OrchestrationTrace trace = new OrchestrationTrace(
                null,
                Instant.now(),
                request.principal().tenantId(),
                request.principal().userId(),
                request.supervisorAgentId(),
                decision.selectedAgentId(),
                request.taskIntent(),
                decision.confidence(),
                status,
                candidates.stream().map(ExpertAgentDefinition::id).toList(),
                steps,
                handoffs,
                Map.of("reason", decision.reason()));
        traces.add(trace);
        telemetry.record(
                TelemetryEventType.ORCHESTRATION,
                request.principal().tenantId(),
                request.principal().userId(),
                request.supervisorAgentId(),
                "orchestration",
                Duration.between(startedAt, Instant.now()),
                Map.of("status", trace.status().name(), "confidence", trace.confidence()));
        return new OrchestrationResult(decision, trace);
    }

    public List<OrchestrationTrace> listTraces(SecurityPrincipal principal) {
        if (!principal.roles().contains("admin")
                && !principal.roles().contains("ops")
                && !principal.roles().contains("auditor")) {
            throw new IllegalStateException("admin, ops, or auditor role is required");
        }
        return traces.stream()
                .filter(trace -> trace.tenantId().equals(principal.tenantId()))
                .toList();
    }
}
