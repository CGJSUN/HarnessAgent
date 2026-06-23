package com.harnessagent.orchestration.application;

import com.harnessagent.agent.runtime.AgentReply;
import com.harnessagent.agent.runtime.AgentRunRequest;
import com.harnessagent.agent.runtime.AgentRuntime;
import com.harnessagent.orchestration.domain.ExpertAgentDefinition;
import com.harnessagent.orchestration.domain.HandoffRecord;
import com.harnessagent.orchestration.domain.OrchestrationStatus;
import com.harnessagent.orchestration.domain.OrchestrationStep;
import com.harnessagent.orchestration.domain.OrchestrationTrace;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.security.application.SensitiveDataRedactor;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolSourceType;
import com.harnessagent.tooling.execution.ToolExecutionCommand;
import com.harnessagent.tooling.execution.ToolExecutor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentAsToolExecutor implements ToolExecutor {

    private final ExpertAgentRegistry registry;
    private final AgentRuntime agentRuntime;
    private final RuntimeContextFactory contextFactory;
    private final OrchestrationTraceStore traceStore;
    private final SensitiveDataRedactor redactor;

    @Autowired
    public AgentAsToolExecutor(
            ExpertAgentRegistry registry,
            AgentRuntime agentRuntime,
            RuntimeContextFactory contextFactory,
            OrchestrationTraceStore traceStore) {
        this(registry, agentRuntime, contextFactory, traceStore, new SensitiveDataRedactor());
    }

    public AgentAsToolExecutor(
            ExpertAgentRegistry registry,
            AgentRuntime agentRuntime,
            RuntimeContextFactory contextFactory,
            OrchestrationTraceStore traceStore,
            SensitiveDataRedactor redactor) {
        this.registry = registry;
        this.agentRuntime = agentRuntime;
        this.contextFactory = contextFactory;
        this.traceStore = traceStore == null ? new OrchestrationTraceStore() : traceStore;
        this.redactor = redactor == null ? new SensitiveDataRedactor() : redactor;
    }

    @Override
    public boolean supports(ToolDefinition definition) {
        return definition.sourceType() == ToolSourceType.AGENT;
    }

    @Override
    public Map<String, Object> execute(ToolDefinition definition, Map<String, Object> parameters) {
        throw new IllegalStateException("Agent tools require caller context.");
    }

    @Override
    public Map<String, Object> execute(
            ToolDefinition definition,
            Map<String, Object> parameters,
            ToolExecutionCommand command) {
        ExpertAgentDefinition child = registry.find(definition.sourceRef())
                .orElseThrow(() -> new IllegalArgumentException("Unknown child agent: " + definition.sourceRef()));
        Map<String, Object> requestedContext = context(parameters.get("context"));
        Map<String, Object> sharedContext = redactor.redactMap(child.contextBoundary().filter(requestedContext));
        String childSessionId = "agent-tool-" + UUID.randomUUID();
        RuntimeContextScope childContext = contextFactory.create(
                command.tenantId(),
                command.userId(),
                child.id(),
                childSessionId);
        String task = string(parameters.get("task"));
        try {
            AgentReply reply = agentRuntime.complete(new AgentRunRequest(
                    childContext,
                    List.of(ChatMessage.user(taskMessage(task, sharedContext))))).block();
            OrchestrationTrace trace = traceStore.save(new OrchestrationTrace(
                    null,
                    Instant.now(),
                    command.tenantId(),
                    command.userId(),
                    command.agentId(),
                    child.id(),
                    task,
                    1d,
                    OrchestrationStatus.EXECUTED,
                    List.of(child.id()),
                    List.of(new OrchestrationStep(
                            null,
                            child.id(),
                            "agent_as_tool_execute",
                            Map.of("task", task, "context", sharedContext),
                            Map.of("result", reply == null ? "" : reply.content()),
                            OrchestrationStatus.EXECUTED)),
                    List.of(new HandoffRecord(
                            Instant.now(),
                            command.agentId(),
                            child.id(),
                            "agent_as_tool",
                            sharedContext)),
                    Map.of("toolId", definition.id(), "toolExecution", "agent_as_tool")));
            return Map.of(
                    "childAgentId", child.id(),
                    "childSessionId", childSessionId,
                    "traceId", trace.id(),
                    "result", reply == null ? "" : reply.content());
        } catch (RuntimeException ex) {
            traceStore.save(new OrchestrationTrace(
                    null,
                    Instant.now(),
                    command.tenantId(),
                    command.userId(),
                    command.agentId(),
                    child.id(),
                    task,
                    1d,
                    OrchestrationStatus.BLOCKED,
                    List.of(child.id()),
                    List.of(new OrchestrationStep(
                            null,
                            child.id(),
                            "agent_as_tool_failed",
                            Map.of("task", task, "context", sharedContext),
                            Map.of("errorType", ex.getClass().getSimpleName(), "message", string(ex.getMessage())),
                            OrchestrationStatus.BLOCKED)),
                    List.of(new HandoffRecord(
                            Instant.now(),
                            command.agentId(),
                            child.id(),
                            "agent_as_tool",
                            sharedContext)),
                    Map.of(
                            "toolId", definition.id(),
                            "toolExecution", "agent_as_tool",
                            "errorType", ex.getClass().getSimpleName())));
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> context(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (key != null && !String.valueOf(key).isBlank()) {
                result.put(String.valueOf(key).trim(), item);
            }
        });
        return Map.copyOf(result);
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String taskMessage(String task, Map<String, Object> sharedContext) {
        if (sharedContext.isEmpty()) {
            return task;
        }
        return task + "\n\nShared context: " + sharedContext;
    }
}
