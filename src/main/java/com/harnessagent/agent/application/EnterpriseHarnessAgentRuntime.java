package com.harnessagent.agent.application;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.model.ModelProviderRegistry;
import com.harnessagent.production.telemetry.RuntimeTelemetry;
import com.harnessagent.production.infrastructure.RuntimeTimeoutGuard;
import com.harnessagent.production.workspace.WorkspaceSnapshotService;
import com.harnessagent.production.telemetry.TelemetryEventType;
import com.harnessagent.production.workspace.WorkspacePlan;
import com.harnessagent.production.workspace.WorkspacePolicyService;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.domain.MessageRole;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.harnessagent.agent.runtime.AgentReply;
import com.harnessagent.agent.runtime.AgentRunRequest;
import com.harnessagent.agent.runtime.AgentRuntime;
import com.harnessagent.agent.runtime.AgentRuntimeEvent;
import com.harnessagent.agent.runtime.AgentRuntimeEventType;

@Component
public class EnterpriseHarnessAgentRuntime implements AgentRuntime {

    private final HarnessAgentProperties properties;
    private final ModelProviderRegistry modelProviderRegistry;
    private final AgentSessionFactory sessionFactory;
    private final WorkspacePolicyService workspacePolicyService;
    private final WorkspaceSnapshotService workspaceSnapshotService;
    private final RuntimeTimeoutGuard timeoutGuard;
    private final RuntimeTelemetry telemetry;

    public EnterpriseHarnessAgentRuntime(
            HarnessAgentProperties properties,
            ModelProviderRegistry modelProviderRegistry,
            AgentSessionFactory sessionFactory,
            WorkspacePolicyService workspacePolicyService,
            WorkspaceSnapshotService workspaceSnapshotService,
            RuntimeTimeoutGuard timeoutGuard,
            RuntimeTelemetry telemetry) {
        this.properties = properties;
        this.modelProviderRegistry = modelProviderRegistry;
        this.sessionFactory = sessionFactory;
        this.workspacePolicyService = workspacePolicyService;
        this.workspaceSnapshotService = workspaceSnapshotService;
        this.timeoutGuard = timeoutGuard;
        this.telemetry = telemetry;
    }

    @Override
    public Mono<AgentReply> complete(AgentRunRequest request) {
        Instant startedAt = Instant.now();
        AgentExecution execution = createExecution(request);
        Mono<AgentReply> work = execution.agent()
                .call(toAgentScopeMessages(request.messages()), execution.runtimeContext())
                .map(message -> new AgentReply(message.getTextContent()))
                .doOnSuccess(ignored -> execution.save());
        return timeoutGuard.guardModel(work)
                .doOnSuccess(reply -> recordAgentTelemetry(request, startedAt, "complete", "succeeded"))
                .doOnError(error -> recordAgentTelemetry(request, startedAt, "complete", "failed"));
    }

    @Override
    public Flux<AgentRuntimeEvent> stream(AgentRunRequest request) {
        Instant startedAt = Instant.now();
        AgentExecution execution = createExecution(request);
        StreamOptions options = StreamOptions.builder()
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT, EventType.SUMMARY)
                .incremental(true)
                .build();
        Flux<AgentRuntimeEvent> work = Flux.concat(
                        Flux.just(AgentRuntimeEvent.status("started")),
                        execution.agent()
                                .stream(toAgentScopeMessages(request.messages()), options, execution.runtimeContext())
                                .map(this::toRuntimeEvent)
                                .filter(event -> event.content() != null && !event.content().isBlank()))
                .concatWithValues(AgentRuntimeEvent.done("completed"))
                .onErrorResume(error -> Flux.just(AgentRuntimeEvent.error(error.getMessage())))
                .doFinally(ignored -> execution.save());
        return timeoutGuard.guardStream(work)
                .doOnComplete(() -> recordAgentTelemetry(request, startedAt, "stream", "succeeded"))
                .doOnError(error -> recordAgentTelemetry(request, startedAt, "stream", "failed"));
    }

    private AgentExecution createExecution(AgentRunRequest request) {
        HarnessAgentProperties.AgentDefinition agentDefinition =
                properties.requireAgent(request.context().agentId());
        String providerId = firstNonBlank(agentDefinition.getModelProvider(), properties.getDefaultProvider());
        Model model = modelProviderRegistry
                .requireProvider(providerId)
                .createModel(agentDefinition.getModelName());
        WorkspacePlan workspacePlan = workspacePolicyService.plan(
                request.context().agentId(),
                agentDefinition.getWorkloadType(),
                agentDefinition.getWorkspace());
        EnterpriseHarnessAgent harnessAgent = EnterpriseHarnessAgent.builder()
                .name(firstNonBlank(agentDefinition.getName(), request.context().agentId()))
                .systemPrompt(agentDefinition.getSystemPrompt())
                .model(model)
                .stateStore(sessionFactory.stateStore(request.context()))
                .defaultSessionId(request.context().runtimeSessionId())
                .workspace(Path.of(nullToEmpty(workspacePlan.location())))
                .compactionEnabled(agentDefinition.isCompaction())
                .maxIters(agentDefinition.getMaxIters())
                .build();
        ReActAgent agent = harnessAgent.delegate();
        RuntimeContext runtimeContext = sessionFactory.runtimeContext(request.context());
        return new AgentExecution(
                agent,
                runtimeContext,
                request.context(),
                workspacePlan,
                harnessAgent.workspace(),
                harnessAgent.compactionEnabled(),
                workspaceSnapshotService);
    }

    private AgentRuntimeEvent toRuntimeEvent(Event event) {
        String content = event.getMessage() == null ? "" : event.getMessage().getTextContent();
        if (event.getType() == EventType.TOOL_RESULT) {
            return new AgentRuntimeEvent(AgentRuntimeEventType.TOOL, content, event.isLast());
        }
        return AgentRuntimeEvent.delta(content);
    }

    private static List<Msg> toAgentScopeMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(EnterpriseHarnessAgentRuntime::toAgentScopeMessage)
                .toList();
    }

    private static Msg toAgentScopeMessage(ChatMessage message) {
        return Msg.builder()
                .name(message.role().name().toLowerCase())
                .role(toAgentScopeRole(message.role()))
                .textContent(message.content())
                .build();
    }

    private static MsgRole toAgentScopeRole(MessageRole role) {
        return switch (role) {
            case USER -> MsgRole.USER;
            case ASSISTANT -> MsgRole.ASSISTANT;
            case SYSTEM -> MsgRole.SYSTEM;
            case TOOL -> MsgRole.TOOL;
        };
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void recordAgentTelemetry(
            AgentRunRequest request, Instant startedAt, String mode, String status) {
        telemetry.record(
                TelemetryEventType.AGENT,
                request.context().tenantId(),
                request.context().userId(),
                request.context().agentId(),
                "agent-runtime",
                Duration.between(startedAt, Instant.now()),
                Map.of("mode", mode, "status", status));
    }

    private record AgentExecution(
            ReActAgent agent,
            RuntimeContext runtimeContext,
            com.harnessagent.runtime.RuntimeContextScope context,
            WorkspacePlan workspacePlan,
            Path workspace,
            boolean compactionEnabled,
            WorkspaceSnapshotService workspaceSnapshotService) {
        void save() {
            agent.saveAgentState(runtimeContext);
            workspaceSnapshotService.save(context, workspacePlan, workspace, "agent-runtime");
        }
    }
}
