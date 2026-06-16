package com.harnessagent.agent.application;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.model.ModelConfigurationResolver;
import com.harnessagent.model.ModelProviderRegistry;
import com.harnessagent.model.ModelSelection;
import com.harnessagent.production.infrastructure.ModelFallbackPlanner;
import com.harnessagent.production.telemetry.RuntimeTelemetry;
import com.harnessagent.production.infrastructure.RuntimeTimeoutGuard;
import com.harnessagent.production.workspace.WorkspaceSnapshotService;
import com.harnessagent.production.telemetry.TelemetryEventType;
import com.harnessagent.production.workspace.WorkspacePlan;
import com.harnessagent.production.workspace.WorkspacePolicyService;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.domain.MessageRole;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.HintBlockEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.ModelCallStartEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.SubagentExposedEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.TextBlockEndEvent;
import io.agentscope.core.event.TextBlockStartEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultDataDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.Model;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.harnessagent.agent.runtime.AgentReply;
import com.harnessagent.agent.runtime.AgentRunRequest;
import com.harnessagent.agent.runtime.AgentRuntime;
import com.harnessagent.agent.runtime.AgentRuntimeEvent;

@Component
public class EnterpriseHarnessAgentRuntime implements AgentRuntime {

    private final HarnessAgentProperties properties;
    private final ModelProviderRegistry modelProviderRegistry;
    private final AgentSessionFactory sessionFactory;
    private final WorkspacePolicyService workspacePolicyService;
    private final WorkspaceSnapshotService workspaceSnapshotService;
    private final RuntimeTimeoutGuard timeoutGuard;
    private final RuntimeTelemetry telemetry;
    private final ModelConfigurationResolver modelConfigurationResolver;
    private final ModelFallbackPlanner fallbackPlanner;

    public EnterpriseHarnessAgentRuntime(
            HarnessAgentProperties properties,
            ModelProviderRegistry modelProviderRegistry,
            AgentSessionFactory sessionFactory,
            WorkspacePolicyService workspacePolicyService,
            WorkspaceSnapshotService workspaceSnapshotService,
            RuntimeTimeoutGuard timeoutGuard,
            RuntimeTelemetry telemetry,
            ModelConfigurationResolver modelConfigurationResolver,
            ModelFallbackPlanner fallbackPlanner) {
        this.properties = properties;
        this.modelProviderRegistry = modelProviderRegistry;
        this.sessionFactory = sessionFactory;
        this.workspacePolicyService = workspacePolicyService;
        this.workspaceSnapshotService = workspaceSnapshotService;
        this.timeoutGuard = timeoutGuard;
        this.telemetry = telemetry;
        this.modelConfigurationResolver = modelConfigurationResolver;
        this.fallbackPlanner = fallbackPlanner;
    }

    @Override
    public Mono<AgentReply> complete(AgentRunRequest request) {
        Instant startedAt = Instant.now();
        ModelSelection primary = modelConfigurationResolver.resolve(request.context().agentId());
        Mono<AgentReply> work = completeWithSelection(request, primary)
                .onErrorResume(error -> fallbackComplete(request, primary, error));
        return timeoutGuard.guardModel(work)
                .doOnSuccess(reply -> recordAgentTelemetry(request, startedAt, "complete", "succeeded"))
                .doOnError(error -> recordAgentTelemetry(request, startedAt, "complete", "failed"));
    }

    @Override
    public Flux<AgentRuntimeEvent> stream(AgentRunRequest request) {
        Instant startedAt = Instant.now();
        ModelSelection primary = modelConfigurationResolver.resolve(request.context().agentId());
        Flux<AgentRuntimeEvent> work = streamWithSelection(request, primary)
                .onErrorResume(error -> fallbackStream(request, primary, error));
        return timeoutGuard.guardStream(work)
                .onErrorResume(error -> Flux.just(AgentRuntimeEvent.error(error.getMessage())))
                .doOnComplete(() -> recordAgentTelemetry(request, startedAt, "stream", "succeeded"))
                .doOnError(error -> recordAgentTelemetry(request, startedAt, "stream", "failed"));
    }

    private Mono<AgentReply> completeWithSelection(AgentRunRequest request, ModelSelection selection) {
        return Mono.defer(() -> {
            AgentExecution execution = createExecution(request, selection);
            return execution.agent()
                    .call(toAgentScopeMessages(request.messages()), execution.runtimeContext())
                    .map(message -> new AgentReply(message.getTextContent()))
                    .doOnSuccess(ignored -> execution.save());
        });
    }

    private Flux<AgentRuntimeEvent> streamWithSelection(AgentRunRequest request, ModelSelection selection) {
        return Flux.defer(() -> {
            AgentExecution execution = createExecution(request, selection);
            AtomicBoolean terminalSent = new AtomicBoolean(false);
            return Flux.concat(
                            Flux.just(AgentRuntimeEvent.status("started", Map.of("modelStatus", "started"))),
                            execution.agent()
                                    .streamEvents(toAgentScopeMessages(request.messages()), execution.runtimeContext())
                                    .map(EnterpriseHarnessAgentRuntime::toRuntimeEvent)
                                    .filter(Objects::nonNull))
                    .doOnNext(event -> {
                        if (event.terminal()) {
                            terminalSent.set(true);
                        }
                    })
                    .concatWith(Mono.defer(() -> terminalSent.get()
                            ? Mono.empty()
                            : Mono.just(AgentRuntimeEvent.done("completed"))))
                    .doFinally(ignored -> execution.save());
        });
    }

    private Mono<AgentReply> fallbackComplete(
            AgentRunRequest request, ModelSelection primary, Throwable failure) {
        List<String> fallbackProviders = fallbackProviders(primary, failure);
        if (fallbackProviders.isEmpty()) {
            return Mono.error(failure);
        }
        Mono<AgentReply> attempt = Mono.error(failure);
        for (String providerId : fallbackProviders) {
            attempt = attempt.onErrorResume(ignored -> completeWithSelection(
                    request,
                    modelConfigurationResolver.resolveFallback(request.context().agentId(), providerId)));
        }
        return attempt;
    }

    private Flux<AgentRuntimeEvent> fallbackStream(
            AgentRunRequest request, ModelSelection primary, Throwable failure) {
        List<String> fallbackProviders = fallbackProviders(primary, failure);
        if (fallbackProviders.isEmpty()) {
            return Flux.error(failure);
        }
        Flux<AgentRuntimeEvent> attempt = Flux.error(failure);
        for (String providerId : fallbackProviders) {
            attempt = attempt.onErrorResume(ignored -> streamWithSelection(
                    request,
                    modelConfigurationResolver.resolveFallback(request.context().agentId(), providerId)));
        }
        return attempt;
    }

    private List<String> fallbackProviders(ModelSelection primary, Throwable failure) {
        if (!primary.fallbackProviders().isEmpty()) {
            if (!fallbackPlanner.isRetryable(failure)) {
                return List.of();
            }
            return primary.fallbackProviders();
        }
        return fallbackPlanner.fallbackProviders(primary.providerId(), failure);
    }

    private AgentExecution createExecution(AgentRunRequest request, ModelSelection selection) {
        HarnessAgentProperties.AgentDefinition agentDefinition =
                properties.requireAgent(request.context().agentId());
        Model model = modelProviderRegistry
                .requireProvider(selection)
                .createModel(selection.providerRequest());
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

    static AgentRuntimeEvent toRuntimeEvent(AgentEvent event) {
        if (event == null) {
            return null;
        }
        Map<String, Object> attributes = baseAttributes(event);
        if (event instanceof ModelCallStartEvent modelStart) {
            put(attributes, "replyId", modelStart.getReplyId());
            put(attributes, "modelStatus", "started");
            return AgentRuntimeEvent.status("model_call_started", attributes);
        }
        if (event instanceof ModelCallEndEvent modelEnd) {
            put(attributes, "replyId", modelEnd.getReplyId());
            put(attributes, "modelStatus", "completed");
            return AgentRuntimeEvent.status("model_call_completed", attributes);
        }
        if (event instanceof TextBlockDeltaEvent textDelta) {
            put(attributes, "replyId", textDelta.getReplyId());
            put(attributes, "blockId", textDelta.getBlockId());
            return AgentRuntimeEvent.delta(nullToEmpty(textDelta.getDelta()), attributes);
        }
        if (event instanceof TextBlockStartEvent textStart) {
            put(attributes, "replyId", textStart.getReplyId());
            put(attributes, "blockId", textStart.getBlockId());
            put(attributes, "textStatus", "started");
            return AgentRuntimeEvent.status("text_block_started", attributes);
        }
        if (event instanceof TextBlockEndEvent textEnd) {
            put(attributes, "replyId", textEnd.getReplyId());
            put(attributes, "blockId", textEnd.getBlockId());
            put(attributes, "textStatus", "completed");
            return AgentRuntimeEvent.status("text_block_completed", attributes);
        }
        if (event instanceof ToolCallStartEvent toolStart) {
            putTool(attributes, toolStart.getReplyId(), toolStart.getToolCallId(), toolStart.getToolCallName());
            put(attributes, "toolStatus", "started");
            return AgentRuntimeEvent.tool(displayName(toolStart.getToolCallName(), "tool_call_started"), attributes);
        }
        if (event instanceof ToolCallDeltaEvent toolDelta) {
            putTool(attributes, toolDelta.getReplyId(), toolDelta.getToolCallId(), toolDelta.getToolCallName());
            put(attributes, "toolStatus", "arguments_delta");
            return AgentRuntimeEvent.tool(nullToEmpty(toolDelta.getDelta()), attributes);
        }
        if (event instanceof ToolCallEndEvent toolEnd) {
            putTool(attributes, toolEnd.getReplyId(), toolEnd.getToolCallId(), toolEnd.getToolCallName());
            put(attributes, "toolStatus", "call_completed");
            return AgentRuntimeEvent.tool(displayName(toolEnd.getToolCallName(), "tool_call_completed"), attributes);
        }
        if (event instanceof ToolResultStartEvent resultStart) {
            putTool(attributes, resultStart.getReplyId(), resultStart.getToolCallId(), resultStart.getToolCallName());
            put(attributes, "toolStatus", "result_started");
            return AgentRuntimeEvent.tool(displayName(resultStart.getToolCallName(), "tool_result_started"), attributes);
        }
        if (event instanceof ToolResultTextDeltaEvent resultDelta) {
            putTool(attributes, resultDelta.getReplyId(), resultDelta.getToolCallId(), resultDelta.getToolCallName());
            put(attributes, "toolStatus", "result_delta");
            return AgentRuntimeEvent.tool(nullToEmpty(resultDelta.getDelta()), attributes);
        }
        if (event instanceof ToolResultDataDeltaEvent dataDelta) {
            putTool(attributes, dataDelta.getReplyId(), dataDelta.getToolCallId(), dataDelta.getToolCallName());
            put(attributes, "toolStatus", "result_data_delta");
            put(attributes, "dataType", dataDelta.getData() == null ? "" : dataDelta.getData().getClass().getSimpleName());
            return AgentRuntimeEvent.tool(displayName(dataDelta.getToolCallName(), "tool_result_data"), attributes);
        }
        if (event instanceof ToolResultEndEvent resultEnd) {
            putTool(attributes, resultEnd.getReplyId(), resultEnd.getToolCallId(), resultEnd.getToolCallName());
            put(attributes, "toolStatus", "result");
            put(attributes, "resultState", resultEnd.getState() == null ? null : resultEnd.getState().name());
            return AgentRuntimeEvent.tool(displayName(resultEnd.getToolCallName(), "tool_result_completed"), attributes);
        }
        if (event instanceof RequireUserConfirmEvent confirm) {
            put(attributes, "replyId", confirm.getReplyId());
            put(attributes, "toolStatus", "waiting_confirmation");
            put(attributes, "toolCalls", toolCalls(confirm.getToolCalls()));
            return AgentRuntimeEvent.tool("tool confirmation required", attributes);
        }
        if (event instanceof SubagentExposedEvent subagent) {
            put(attributes, "subagentId", subagent.getSubagentId());
            put(attributes, "agentId", subagent.getAgentId());
            put(attributes, "sessionId", subagent.getSessionId());
            put(attributes, "label", subagent.getLabel());
            return AgentRuntimeEvent.subagent(displayName(subagent.getLabel(), "subagent exposed"), attributes);
        }
        if (event instanceof AgentResultEvent result) {
            put(attributes, "agentStatus", "result");
            String content = result.getResult() == null ? "" : result.getResult().getTextContent();
            if (hasSource(event)) {
                return AgentRuntimeEvent.subagent(content, attributes);
            }
            return AgentRuntimeEvent.done(content, attributes);
        }
        if (event instanceof AgentStartEvent start) {
            put(attributes, "sessionId", start.getSessionId());
            put(attributes, "replyId", start.getReplyId());
            put(attributes, "agentName", start.getName());
            put(attributes, "agentRole", start.getRole());
            put(attributes, "agentStatus", "started");
            if (hasSource(event)) {
                return AgentRuntimeEvent.subagent(displayName(start.getName(), "subagent started"), attributes);
            }
            return AgentRuntimeEvent.status("agent_started", attributes);
        }
        if (event instanceof AgentEndEvent end) {
            put(attributes, "replyId", end.getReplyId());
            put(attributes, "agentStatus", "completed");
            if (hasSource(event)) {
                return AgentRuntimeEvent.subagent("subagent completed", attributes);
            }
            return AgentRuntimeEvent.status("agent_completed", attributes);
        }
        if (event instanceof HintBlockEvent hint) {
            put(attributes, "replyId", hint.getReplyId());
            put(attributes, "blockId", hint.getBlockId());
            put(attributes, "hintSource", hint.getHintSource());
            return AgentRuntimeEvent.status(nullToEmpty(hint.getHint()), attributes);
        }
        if (event.getType() == AgentEventType.EXCEED_MAX_ITERS
                || event.getType() == AgentEventType.REQUEST_STOP) {
            return AgentRuntimeEvent.error(event.getType().name().toLowerCase());
        }
        if (hasSource(event)) {
            return AgentRuntimeEvent.subagent(event.getType().name().toLowerCase(), attributes);
        }
        return AgentRuntimeEvent.status(event.getType().name().toLowerCase(), attributes);
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

    private static Map<String, Object> baseAttributes(AgentEvent event) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        put(attributes, "agentEventType", event.getType().name());
        put(attributes, "eventId", event.getId());
        put(attributes, "source", event.getSource());
        if (event.getMetadata() != null) {
            event.getMetadata().forEach((key, value) -> put(attributes, key, value));
        }
        return attributes;
    }

    private static void putTool(
            Map<String, Object> attributes, String replyId, String toolCallId, String toolCallName) {
        put(attributes, "replyId", replyId);
        put(attributes, "toolCallId", toolCallId);
        put(attributes, "toolName", toolCallName);
    }

    private static List<Map<String, Object>> toolCalls(List<ToolUseBlock> calls) {
        if (calls == null) {
            return List.of();
        }
        return calls.stream()
                .map(call -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    put(item, "toolCallId", call.getId());
                    put(item, "toolName", call.getName());
                    put(item, "parameters", call.getInput());
                    put(item, "state", call.getState() == null ? null : call.getState().name());
                    return item;
                })
                .toList();
    }

    private static boolean hasSource(AgentEvent event) {
        return event.getSource() != null && !event.getSource().isBlank();
    }

    private static String displayName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void put(Map<String, Object> attributes, String key, Object value) {
        if (value != null) {
            attributes.put(key, value);
        }
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
