package com.harnessagent.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.agent.runtime.AgentReply;
import com.harnessagent.agent.runtime.AgentRunRequest;
import com.harnessagent.agent.runtime.AgentRuntimeEvent;
import com.harnessagent.agent.runtime.AgentRuntimeEventType;
import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.model.EchoModelProvider;
import com.harnessagent.model.ModelConfigurationResolver;
import com.harnessagent.model.ModelProvider;
import com.harnessagent.model.ModelProviderRegistry;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.health.ProductionRuntimeValidator;
import com.harnessagent.production.infrastructure.ModelFallbackPlanner;
import com.harnessagent.production.infrastructure.RetryableModelException;
import com.harnessagent.production.infrastructure.RuntimeTimeoutGuard;
import com.harnessagent.production.infrastructure.LocalJsonAgentStateStore;
import com.harnessagent.production.snapshot.SnapshotStore;
import com.harnessagent.production.state.AgentStateStoreFactory;
import com.harnessagent.production.state.StateStorePlan;
import com.harnessagent.production.state.OwnerStateKeyStrategy;
import com.harnessagent.production.telemetry.RuntimeTelemetry;
import com.harnessagent.production.workspace.WorkspacePolicyService;
import com.harnessagent.production.workspace.WorkspaceSnapshotService;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.session.domain.ChatMessage;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.SubagentExposedEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class PersonalHarnessAgentRuntimeTest {

    @TempDir
    Path tempDir;

    @Test
    void mapsFineGrainedToolAndHitlEvents() {
        AgentRuntimeEvent toolStart = PersonalHarnessAgentRuntime.toRuntimeEvent(
                new ToolCallStartEvent("reply-1", "call-1", "search.docs"));
        AgentRuntimeEvent waitingConfirmation = PersonalHarnessAgentRuntime.toRuntimeEvent(
                new RequireUserConfirmEvent(
                        "reply-1",
                        List.of(new ToolUseBlock("call-2", "write.file", Map.of("path", "draft.md")))));

        assertThat(toolStart.type()).isEqualTo(AgentRuntimeEventType.TOOL);
        assertThat(toolStart.attributes()).containsEntry("toolStatus", "started");
        assertThat(toolStart.attributes()).containsEntry("toolName", "search.docs");
        assertThat(waitingConfirmation.type()).isEqualTo(AgentRuntimeEventType.TOOL);
        assertThat(waitingConfirmation.attributes()).containsEntry("toolStatus", "waiting_confirmation");
        assertThat(waitingConfirmation.attributes()).containsKey("toolCalls");
    }

    @Test
    void mapsSubagentEventsAndSourcedResults() {
        AgentRuntimeEvent exposed = PersonalHarnessAgentRuntime.toRuntimeEvent(
                new SubagentExposedEvent("sub-1", "researcher", "session-sub", "Researcher"));
        AgentRuntimeEvent sourcedResult = PersonalHarnessAgentRuntime.toRuntimeEvent(
                new AgentResultEvent(message("research complete")).withSource("main/researcher"));

        assertThat(exposed.type()).isEqualTo(AgentRuntimeEventType.SUBAGENT);
        assertThat(exposed.attributes()).containsEntry("subagentId", "sub-1");
        assertThat(exposed.attributes()).containsEntry("agentId", "researcher");
        assertThat(sourcedResult.type()).isEqualTo(AgentRuntimeEventType.SUBAGENT);
        assertThat(sourcedResult.content()).isEqualTo("research complete");
        assertThat(sourcedResult.attributes()).containsEntry("source", "main/researcher");
    }

    @Test
    void mapsTextDeltaAndTopLevelResult() {
        AgentRuntimeEvent delta = PersonalHarnessAgentRuntime.toRuntimeEvent(
                new TextBlockDeltaEvent("reply-1", "block-1", "hello"));
        AgentRuntimeEvent done = PersonalHarnessAgentRuntime.toRuntimeEvent(
                new AgentResultEvent(message("completed")));

        assertThat(delta.type()).isEqualTo(AgentRuntimeEventType.DELTA);
        assertThat(delta.content()).isEqualTo("hello");
        assertThat(done.type()).isEqualTo(AgentRuntimeEventType.DONE);
        assertThat(done.terminal()).isTrue();
        assertThat(done.content()).isEqualTo("completed");
    }

    @Test
    void fallsBackToConfiguredProviderWhenPrimaryModelFails() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        properties.getState().setLocalDirectory(tempDir.resolve("sessions"));
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setName("agent-a");
        agent.setSystemPrompt("Answer as a personal assistant.");
        agent.setModelProvider("primary");
        agent.setModelName("primary-model");
        agent.setWorkspace(tempDir.resolve("workspace").toString());
        agent.setFallbackProviders(List.of("echo"));
        properties.getAgents().put("agent-a", agent);
        HarnessAgentProperties.ModelProviderDefinition primary = new HarnessAgentProperties.ModelProviderDefinition();
        primary.setModelName("primary-model");
        HarnessAgentProperties.ModelProviderDefinition echo = new HarnessAgentProperties.ModelProviderDefinition();
        echo.setModelName("echo-local");
        properties.getModelProviders().put("primary", primary);
        properties.getModelProviders().put("echo", echo);
        ProductionRuntimeProperties runtimeProperties = new ProductionRuntimeProperties();
        FailingModelProvider failingProvider = new FailingModelProvider();
        PersonalHarnessAgentRuntime runtime = new PersonalHarnessAgentRuntime(
                properties,
                new ModelProviderRegistry(List.of(failingProvider, new EchoModelProvider())),
                new AgentSessionFactory(
                        properties,
                        new ProductionRuntimeValidator(runtimeProperties),
                        plan -> new LocalJsonAgentStateStore(
                                new OwnerStateKeyStrategy(),
                                StateStorePlan.local(tempDir.resolve("state").toString()))),
                new WorkspacePolicyService(runtimeProperties),
                new WorkspaceSnapshotService(new EmptyObjectProvider<>()),
                new RuntimeTimeoutGuard(runtimeProperties),
                RuntimeTelemetry.noop(),
                new ModelConfigurationResolver(properties, runtimeProperties),
                new ModelFallbackPlanner(runtimeProperties));

        AgentReply reply = runtime.complete(new AgentRunRequest(
                        new RuntimeContextFactory().create("owner-scope-a", "user-a", "agent-a", "session-a"),
                        List.of(ChatMessage.user("hello"))))
                .block();

        assertThat(reply.content()).contains("Echo: hello");
        assertThat(failingProvider.calls()).isEqualTo(1);
    }

    private static Msg message(String text) {
        return Msg.builder()
                .name("assistant")
                .role(MsgRole.ASSISTANT)
                .textContent(text)
                .build();
    }

    private static class FailingModelProvider implements ModelProvider {

        private int calls;

        @Override
        public String id() {
            return "primary";
        }

        @Override
        public Model createModel(String requestedModelName) {
            calls++;
            return new FailingModel(requestedModelName);
        }

        private int calls() {
            return calls;
        }
    }

    private record FailingModel(String modelName) implements Model {

        @Override
        public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            return Flux.error(new RetryableModelException(503, "primary provider busy"));
        }

        @Override
        public String getModelName() {
            return modelName;
        }
    }

    private static class EmptyObjectProvider<T> implements ObjectProvider<T> {

        @Override
        public T getObject(Object... args) {
            return null;
        }

        @Override
        public T getIfAvailable() {
            return null;
        }

        @Override
        public T getIfUnique() {
            return null;
        }

        @Override
        public T getObject() {
            return null;
        }
    }
}
