package com.harnessagent.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harnessagent.agent.runtime.AgentReply;
import com.harnessagent.agent.runtime.AgentRunRequest;
import com.harnessagent.agent.runtime.AgentRuntime;
import com.harnessagent.agent.runtime.AgentRuntimeChannel;
import com.harnessagent.agent.runtime.AgentRuntimeEvent;
import com.harnessagent.agent.runtime.AgentRuntimeEventType;
import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.model.ModelConfigurationResolver;
import com.harnessagent.production.infrastructure.AgentScopeStateStoreAdapter;
import com.harnessagent.production.budget.BudgetCounter;
import com.harnessagent.production.budget.BudgetCounterStore;
import com.harnessagent.production.budget.BudgetLimiter;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.infrastructure.InMemoryAgentStateStore;
import com.harnessagent.production.telemetry.RuntimeTelemetry;
import com.harnessagent.production.state.StateStorePlan;
import com.harnessagent.production.state.TenantStateKeyStrategy;
import com.harnessagent.rag.persistence.InMemoryKnowledgeStore;
import com.harnessagent.rag.application.KnowledgeDocumentInput;
import com.harnessagent.rag.application.KnowledgeRetrievalPolicy;
import com.harnessagent.rag.application.KnowledgeService;
import com.harnessagent.rag.domain.KnowledgeSourceRegistration;
import com.harnessagent.rag.domain.KnowledgeVisibility;
import com.harnessagent.rag.application.TextChunker;
import com.harnessagent.rag.application.TextTokenizer;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.security.application.PromptInjectionGuard;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.domain.MessageRole;
import com.harnessagent.session.persistence.InMemorySessionStore;
import com.harnessagent.workspace.application.ContextCompactionService;
import com.harnessagent.workspace.application.PersonalWorkspaceService;
import io.agentscope.core.state.AgentState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import com.harnessagent.chat.application.ChatService;
import com.harnessagent.chat.domain.ChatCommand;
import com.harnessagent.chat.domain.ChatResult;

class ChatServiceTest {

    @TempDir
    Path tempDir;

    private final RuntimeContextFactory contextFactory = new RuntimeContextFactory();
    private final InMemorySessionStore sessionStore = new InMemorySessionStore();
    private final RecordingAgentRuntime agentRuntime = new RecordingAgentRuntime();
    private final KnowledgeService knowledgeService = new KnowledgeService(
            new InMemoryKnowledgeStore(),
            new TextChunker(),
            new TextTokenizer(),
            new KnowledgeRetrievalPolicy());
    private final ChatService chatService =
            new ChatService(contextFactory, sessionStore, agentRuntime, knowledgeService);

    @Test
    void sendsDerivedRuntimeContextToAgentAndPersistsMessages() {
        ChatResult result = chatService.chat(command("hello")).block();

        assertThat(result.message()).isEqualTo("answer:hello");
        assertThat(result.messageId()).isNotBlank();
        assertThat(result.sessionId()).isEqualTo("session-a");
        assertThat(result.contentBlocks()).singleElement()
                .satisfies(block -> {
                    assertThat(block.type().name()).isEqualTo("TEXT");
                    assertThat(block.text()).isEqualTo("answer:hello");
                });
        assertThat(result.executionSummary().status()).isEqualTo("completed");
        assertThat(result.executionSummary().runtimeSessionId()).isEqualTo("agent-a:session-a");
        assertThat(agentRuntime.requests).hasSize(1);
        AgentRunRequest request = agentRuntime.requests.get(0);
        assertThat(request.context().runtimeUserId()).isEqualTo("tenant-a:user-a");
        assertThat(request.context().runtimeSessionId()).isEqualTo("agent-a:session-a");
        List<ChatMessage> storedMessages = sessionStore.listMessages(request.context());
        assertThat(storedMessages)
                .extracting(ChatMessage::content)
                .containsExactly("hello", "answer:hello");
        assertThat(result.messageId()).isEqualTo(storedMessages.get(1).id());
    }

    @Test
    void normalizesBlankEnterpriseIdentityToPersonalContextBeforeGovernance() {
        ChatResult result = chatService.chat(new ChatCommand(
                null,
                " ",
                "personal-agent",
                "session-a",
                "hello personal",
                false,
                Set.of(),
                Set.of(),
                5)).block();

        assertThat(result.runtimeUserId()).isEqualTo("personal:personal-user");
        assertThat(result.sessionId()).isEqualTo("session-a");
        AgentRunRequest request = agentRuntime.requests.get(0);
        assertThat(request.context().tenantId()).isEqualTo("personal");
        assertThat(request.context().userId()).isEqualTo("personal-user");
        assertThat(request.context().runtimeSessionId()).isEqualTo("personal-agent:session-a");
    }

    @Test
    void isolatesMessagesForDifferentPersonalAgentsWithSameOwnerAndSessionId() {
        chatService.chat(new ChatCommand(
                "personal",
                "owner-a",
                "agent-a",
                "session-a",
                "hello agent a",
                false,
                Set.of(),
                Set.of(),
                5)).block();
        chatService.chat(new ChatCommand(
                "personal",
                "owner-a",
                "agent-b",
                "session-a",
                "hello agent b",
                false,
                Set.of(),
                Set.of(),
                5)).block();

        assertThat(sessionStore.listMessages(contextFactory.create("personal", "owner-a", "agent-a", "session-a")))
                .extracting(ChatMessage::content)
                .containsExactly("hello agent a", "answer:hello agent a");
        assertThat(sessionStore.listMessages(contextFactory.create("personal", "owner-a", "agent-b", "session-a")))
                .extracting(ChatMessage::content)
                .containsExactly("hello agent b", "answer:hello agent b");
        assertThat(agentRuntime.requests)
                .extracting(request -> request.context().runtimeSessionId())
                .containsExactly("agent-a:session-a", "agent-b:session-a");
    }

    @Test
    void streamsEventsAndPersistsAssistantMessageOnCompletion() {
        StepVerifier.create(chatService.stream(command("stream me")))
                .expectNextMatches(event -> event.content().equals("started")
                        && event.channel() == AgentRuntimeChannel.SYSTEM_NOTICE)
                .expectNextMatches(event -> event.content().equals("chunk-1")
                        && event.channel() == AgentRuntimeChannel.USER_VISIBLE)
                .expectNextMatches(event -> event.content().equals("chunk-2"))
                .expectNextMatches(event -> event.type() == AgentRuntimeEventType.TOOL
                        && event.channel() == AgentRuntimeChannel.TOOL_EVENT
                        && event.attributes().get("toolStatus").equals("started"))
                .expectNextMatches(event -> event.type() == AgentRuntimeEventType.SUBAGENT
                        && event.channel() == AgentRuntimeChannel.DIAGNOSTIC
                        && event.attributes().get("subagentId").equals("researcher"))
                .expectNextMatches(event -> event.content().equals("completed")
                        && event.channel() == AgentRuntimeChannel.USER_VISIBLE)
                .verifyComplete();

        assertThat(sessionStore.listMessages(agentRuntime.requests.get(0).context()))
                .extracting(ChatMessage::content)
                .containsExactly("stream me", "chunk-1chunk-2");
    }

    @Test
    void streamPersistsToolResultEventsAsContentBlocks() {
        ToolResultAgentRuntime runtime = new ToolResultAgentRuntime();
        ChatService service = new ChatService(contextFactory, sessionStore, runtime, knowledgeService);

        StepVerifier.create(service.stream(command("stream with tool")))
                .expectNextCount(5)
                .verifyComplete();

        List<ChatMessage> messages = sessionStore.listMessages(runtime.requests.get(0).context());
        assertThat(messages).hasSize(2);
        ChatMessage assistant = messages.get(1);
        assertThat(assistant.content()).isEqualTo("answer");
        assertThat(assistant.contentBlocks()).hasSize(2);
        assertThat(assistant.contentBlocks().get(0).type().name()).isEqualTo("TEXT");
        assertThat(assistant.contentBlocks().get(1).type().name()).isEqualTo("TOOL_RESULT");
        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>) assistant.contentBlocks().get(1).metadata().get("result");
        assertThat(result)
                .containsEntry("toolStatus", "result")
                .containsEntry("rawReference", "workspace://artifacts/search/raw.json")
                .containsEntry("text", "{\"matches\":2}");
    }


    @Test
    void compactsLongContextBeforeCallingRuntimeWithoutDeletingSessionHistory() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setWorkspace(tempDir.resolve("agent-a").toString());
        agent.setCompactionMessageThreshold(3);
        properties.getAgents().put("agent-a", agent);
        ProductionRuntimeProperties runtimeProperties = new ProductionRuntimeProperties();
        ChatService service = new ChatService(
                contextFactory,
                sessionStore,
                agentRuntime,
                knowledgeService,
                RuntimeTelemetry.noop(),
                new BudgetLimiter(runtimeProperties, new RecordingBudgetCounterStore()),
                properties,
                new ModelConfigurationResolver(properties, runtimeProperties),
                AgentSessionRecoveryService.noop(sessionStore),
                new PromptInjectionGuard(),
                new ContextCompactionService(new PersonalWorkspaceService(properties), properties));
        com.harnessagent.runtime.RuntimeContextScope context =
                contextFactory.create("tenant-a", "user-a", "agent-a", "session-a");
        sessionStore.appendMessage(context, ChatMessage.user("Goal: finish workspace runtime."));
        sessionStore.appendMessage(context, ChatMessage.assistant("finding: workspace snapshot is ready."));
        sessionStore.appendMessage(context, ChatMessage.user("Decision: persist the summary. Next: test compaction."));

        service.chat(command("continue with latest request")).block();

        List<ChatMessage> runtimeMessages = agentRuntime.requests.get(0).messages();
        assertThat(runtimeMessages).hasSize(2);
        assertThat(runtimeMessages.get(0).role()).isEqualTo(MessageRole.SYSTEM);
        assertThat(runtimeMessages.get(0).content()).contains("Context compaction summary.");
        assertThat(runtimeMessages.get(0).contentBlocks())
                .anySatisfy(block -> assertThat(block.uri()).startsWith("workspace://sessions/"));
        assertThat(runtimeMessages.get(1).content()).isEqualTo("continue with latest request");
        assertThat(sessionStore.listMessages(context))
                .extracting(ChatMessage::content)
                .contains(
                        "Goal: finish workspace runtime.",
                        "finding: workspace snapshot is ready.",
                        "Decision: persist the summary. Next: test compaction.",
                        "continue with latest request",
                        "answer:continue with latest request");
    }

    @Test
    void injectsKnowledgeAndReturnsCitationsWhenEnabled() {
        knowledgeService.ingestDocument(new KnowledgeDocumentInput(
                new KnowledgeSourceRegistration(
                        "tenant-a",
                        "owner-a",
                        "报销制度",
                        "v1",
                        KnowledgeVisibility.PUBLIC,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        "manual"),
                "发票需要在三十天内提交。"));

        ChatResult result = chatService.chat(new ChatCommand(
                "tenant-a",
                "user-a",
                "agent-a",
                "session-rag",
                "发票多久提交",
                true,
                Set.of(),
                Set.of(),
                3)).block();

        assertThat(result.knowledgeBacked()).isTrue();
        assertThat(result.citations()).hasSize(1);
        assertThat(result.executionSummary().knowledgeBacked()).isTrue();
        assertThat(result.executionSummary().citationCount()).isEqualTo(1);
        List<ChatMessage> agentMessages = agentRuntime.requests.get(0).messages();
        String augmentedPrompt = agentMessages.get(agentMessages.size() - 1).content();
        assertThat(augmentedPrompt)
                .contains("请仅基于以下可访问知识回答用户问题")
                .contains("发票需要在三十天内提交");
    }

    @Test
    void configuredExternalMemoryRagProviderFailsBeforeModelCallWhenNotWired() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        properties.getMemoryRag().setProvider("mem0");
        ProductionRuntimeProperties runtimeProperties = new ProductionRuntimeProperties();
        ChatService service = new ChatService(
                contextFactory,
                sessionStore,
                agentRuntime,
                knowledgeService,
                RuntimeTelemetry.noop(),
                new BudgetLimiter(runtimeProperties, new RecordingBudgetCounterStore()),
                properties,
                new ModelConfigurationResolver(properties, runtimeProperties),
                AgentSessionRecoveryService.noop(sessionStore),
                new PromptInjectionGuard());

        assertThatThrownBy(() -> service.chat(new ChatCommand(
                        "tenant-a",
                        "user-a",
                        "agent-a",
                        "session-rag-provider",
                        "发票多久提交",
                        true,
                        Set.of(),
                        Set.of(),
                        3)).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Memory/RAG provider mem0 is not configured");
        assertThat(agentRuntime.requests).isEmpty();
    }

    @Test
    void streamsCitationMetadataWhenKnowledgeIsUsed() {
        knowledgeService.ingestDocument(new KnowledgeDocumentInput(
                new KnowledgeSourceRegistration(
                        "tenant-a",
                        "owner-a",
                        "报销制度",
                        "v1",
                        KnowledgeVisibility.PUBLIC,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        "manual"),
                "发票需要在三十天内提交。"));

        StepVerifier.create(chatService.stream(new ChatCommand(
                        "tenant-a",
                        "user-a",
                        "agent-a",
                        "session-rag-stream",
                        "发票多久提交",
                        true,
                        Set.of(),
                        Set.of(),
                        3)))
                .expectNextCount(5)
                .assertNext(event -> {
                    assertThat(event.type()).isEqualTo(AgentRuntimeEventType.DONE);
                    assertThat(event.attributes()).containsKey("citations");
                })
                .verifyComplete();
    }

    @Test
    void returnsNoAnswerWithoutCallingAgentWhenEvidenceIsMissing() {
        ChatResult result = chatService.chat(new ChatCommand(
                "tenant-a",
                "user-a",
                "agent-a",
                "session-no-answer",
                "没有知识的问题",
                true,
                Set.of(),
                Set.of(),
                3)).block();

        assertThat(result.noAnswerReason()).contains("无法从当前可用知识中确定答案");
        assertThat(result.messageId()).isNotBlank();
        assertThat(result.sessionId()).isEqualTo("session-no-answer");
        assertThat(result.contentBlocks()).singleElement()
                .satisfies(block -> assertThat(block.text()).contains("无法从当前可用知识中确定答案"));
        assertThat(result.executionSummary().status()).isEqualTo("knowledge_no_answer");
        assertThat(agentRuntime.requests).isEmpty();
        List<ChatMessage> storedMessages = sessionStore.listMessages(
                contextFactory.create("tenant-a", "user-a", "agent-a", "session-no-answer"));
        assertThat(storedMessages)
                .extracting(ChatMessage::content)
                .containsExactly("没有知识的问题", result.message());
        assertThat(result.messageId()).isEqualTo(storedMessages.get(1).id());
    }

    @Test
    void streamsNoAnswerMetadataWhenEvidenceIsMissing() {
        StepVerifier.create(chatService.stream(new ChatCommand(
                        "tenant-a",
                        "user-a",
                        "agent-a",
                        "session-no-answer-stream",
                        "没有知识的问题",
                        true,
                        Set.of(),
                        Set.of(),
                        3)))
                .assertNext(event -> assertThat(event.attributes()).containsKey("noAnswerReason"))
                .expectNextMatches(event -> event.content().contains("无法从当前可用知识中确定答案"))
                .expectNextMatches(event -> event.attributes().containsKey("noAnswerReason"))
                .verifyComplete();

        assertThat(agentRuntime.requests).isEmpty();
    }

    @Test
    void budgetsAgainstAgentModelProviderAndAgentLevelLimit() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        properties.setDefaultProvider("echo");
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setModelProvider("dashscope");
        agent.setModelName("qwen-plus");
        agent.getBudget().setRequestLimit(1L);
        properties.getAgents().put("agent-a", agent);
        HarnessAgentProperties.ModelProviderDefinition dashscope = new HarnessAgentProperties.ModelProviderDefinition();
        dashscope.setModelName("qwen-plus");
        properties.getModelProviders().put("dashscope", dashscope);
        ProductionRuntimeProperties runtimeProperties = new ProductionRuntimeProperties();
        runtimeProperties.getBudget().setRequestLimit(100);
        runtimeProperties.getBudget().setTokenLimit(1000);
        RecordingBudgetCounterStore budgetStore = new RecordingBudgetCounterStore();
        ChatService service = new ChatService(
                contextFactory,
                sessionStore,
                agentRuntime,
                knowledgeService,
                RuntimeTelemetry.noop(),
                new BudgetLimiter(runtimeProperties, budgetStore),
                properties,
                new ModelConfigurationResolver(properties, runtimeProperties),
                AgentSessionRecoveryService.noop(sessionStore),
                new PromptInjectionGuard());

        service.chat(command("first")).block();

        assertThat(budgetStore.keys()).contains("provider:dashscope");
        assertThat(budgetStore.keys()).doesNotContain("provider:echo");
        assertThatThrownBy(() -> service.chat(new ChatCommand(
                        "tenant-a",
                        "user-a",
                        "agent-a",
                        "session-budget-2",
                        "second",
                        false,
                        Set.of(),
                        Set.of(),
                        5)).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent:tenant-a:agent-a:request_limit_exceeded");
    }

    @Test
    void recordsPendingExecutionDuringAgentCallAndClearsItAfterSuccess() {
        InMemoryAgentStateStore stateStore = new InMemoryAgentStateStore(
                new TenantStateKeyStrategy(),
                StateStorePlan.local(".state"));
        AgentSessionRecoveryService recoveryService = new AgentSessionRecoveryService(
                sessionStore,
                new com.harnessagent.production.health.ProductionRuntimeValidator(new ProductionRuntimeProperties()),
                plan -> stateStore);
        RecoveryAwareAgentRuntime runtime = new RecoveryAwareAgentRuntime(recoveryService);
        HarnessAgentProperties properties = new HarnessAgentProperties();
        ProductionRuntimeProperties runtimeProperties = new ProductionRuntimeProperties();
        ChatService service = new ChatService(
                contextFactory,
                sessionStore,
                runtime,
                knowledgeService,
                RuntimeTelemetry.noop(),
                new BudgetLimiter(runtimeProperties, new RecordingBudgetCounterStore()),
                properties,
                new ModelConfigurationResolver(properties, runtimeProperties),
                recoveryService,
                new PromptInjectionGuard());

        ChatResult result = service.chat(command("recoverable")).block();

        assertThat(result.message()).isEqualTo("answer:recoverable");
        assertThat(runtime.sawPending).isTrue();
        assertThat(recoveryService.pendingExecution(runtime.lastContext)).isEmpty();
    }

    @Test
    void clearsPendingExecutionWhenStreamIsCancelled() {
        InMemoryAgentStateStore stateStore = new InMemoryAgentStateStore(
                new TenantStateKeyStrategy(),
                StateStorePlan.local(".state"));
        AgentSessionRecoveryService recoveryService = new AgentSessionRecoveryService(
                sessionStore,
                new com.harnessagent.production.health.ProductionRuntimeValidator(new ProductionRuntimeProperties()),
                plan -> stateStore);
        CancellableAgentRuntime runtime = new CancellableAgentRuntime();
        HarnessAgentProperties properties = new HarnessAgentProperties();
        ProductionRuntimeProperties runtimeProperties = new ProductionRuntimeProperties();
        ChatService service = new ChatService(
                contextFactory,
                sessionStore,
                runtime,
                knowledgeService,
                RuntimeTelemetry.noop(),
                new BudgetLimiter(runtimeProperties, new RecordingBudgetCounterStore()),
                properties,
                new ModelConfigurationResolver(properties, runtimeProperties),
                recoveryService,
                new PromptInjectionGuard());

        StepVerifier.create(service.stream(command("cancel me")))
                .thenCancel()
                .verify();

        assertThat(runtime.cancelled.get()).isTrue();
        assertThat(recoveryService.pendingExecution(contextFactory.create("tenant-a", "user-a", "agent-a", "session-a")))
                .isEmpty();
    }

    @Test
    void seedsAgentWithStoredMessageHistoryWhenAgentScopeStateIsMissing() {
        InMemoryAgentStateStore stateStore = new InMemoryAgentStateStore(
                new TenantStateKeyStrategy(),
                StateStorePlan.local(".state"));
        AgentSessionRecoveryService recoveryService = new AgentSessionRecoveryService(
                sessionStore,
                new com.harnessagent.production.health.ProductionRuntimeValidator(new ProductionRuntimeProperties()),
                plan -> stateStore);
        ChatService service = chatService(recoveryService);
        com.harnessagent.runtime.RuntimeContextScope context =
                contextFactory.create("tenant-a", "user-a", "agent-a", "session-a");
        sessionStore.appendMessage(context, ChatMessage.user("old question"));
        sessionStore.appendMessage(context, ChatMessage.assistant("old answer"));

        service.chat(command("new question")).block();

        assertThat(agentRuntime.requests).hasSize(1);
        assertThat(agentRuntime.requests.get(0).messages())
                .extracting(ChatMessage::content)
                .containsExactly("old question", "old answer", "new question");
        assertThat(sessionStore.listMessages(context))
                .extracting(ChatMessage::content)
                .containsExactly("old question", "old answer", "new question", "answer:new question");
    }

    @Test
    void sendsOnlyCurrentTurnWhenAgentScopeStateAlreadyExists() {
        InMemoryAgentStateStore stateStore = new InMemoryAgentStateStore(
                new TenantStateKeyStrategy(),
                StateStorePlan.local(".state"));
        AgentSessionRecoveryService recoveryService = new AgentSessionRecoveryService(
                sessionStore,
                new com.harnessagent.production.health.ProductionRuntimeValidator(new ProductionRuntimeProperties()),
                plan -> stateStore);
        ChatService service = chatService(recoveryService);
        com.harnessagent.runtime.RuntimeContextScope context =
                contextFactory.create("tenant-a", "user-a", "agent-a", "session-a");
        sessionStore.appendMessage(context, ChatMessage.user("old question"));
        sessionStore.appendMessage(context, ChatMessage.assistant("old answer"));
        stateStore.save(context, agentScope(context, "agent_state"), "{\"remembered\":true}");

        service.chat(command("new question")).block();

        assertThat(recoveryService.agentScopeStatePresent(context)).isTrue();
        assertThat(agentRuntime.requests).hasSize(1);
        assertThat(agentRuntime.requests.get(0).messages())
                .extracting(ChatMessage::content)
                .containsExactly("new question");
        assertThat(sessionStore.listMessages(context))
                .extracting(ChatMessage::content)
                .containsExactly("old question", "old answer", "new question", "answer:new question");
    }

    @Test
    void recognizesAgentScopeAdapterSavedAgentStateAsRecoverableContext() {
        InMemoryAgentStateStore stateStore = new InMemoryAgentStateStore(
                new TenantStateKeyStrategy(),
                StateStorePlan.local(".state"));
        AgentSessionRecoveryService recoveryService = new AgentSessionRecoveryService(
                sessionStore,
                new com.harnessagent.production.health.ProductionRuntimeValidator(new ProductionRuntimeProperties()),
                plan -> stateStore);
        ChatService service = chatService(recoveryService);
        com.harnessagent.runtime.RuntimeContextScope context =
                contextFactory.create("tenant-a", "user-a", "agent-a", "session-a");
        sessionStore.appendMessage(context, ChatMessage.user("old question"));
        sessionStore.appendMessage(context, ChatMessage.assistant("old answer"));
        new AgentScopeStateStoreAdapter(context, stateStore)
                .save(
                        context.runtimeUserId(),
                        context.runtimeSessionId(),
                        "agent_state",
                        AgentState.builder()
                                .userId(context.runtimeUserId())
                                .sessionId(context.runtimeSessionId())
                                .build());

        service.chat(command("new question")).block();

        assertThat(recoveryService.agentScopeStatePresent(context)).isTrue();
        assertThat(agentRuntime.requests).hasSize(1);
        assertThat(agentRuntime.requests.get(0).messages())
                .extracting(ChatMessage::content)
                .containsExactly("new question");
    }

    @Test
    void replaysStoredHistoryWhenOnlyAuxiliaryAgentScopeStateExists() {
        InMemoryAgentStateStore stateStore = new InMemoryAgentStateStore(
                new TenantStateKeyStrategy(),
                StateStorePlan.local(".state"));
        AgentSessionRecoveryService recoveryService = new AgentSessionRecoveryService(
                sessionStore,
                new com.harnessagent.production.health.ProductionRuntimeValidator(new ProductionRuntimeProperties()),
                plan -> stateStore);
        ChatService service = chatService(recoveryService);
        com.harnessagent.runtime.RuntimeContextScope context =
                contextFactory.create("tenant-a", "user-a", "agent-a", "session-a");
        sessionStore.appendMessage(context, ChatMessage.user("old question"));
        sessionStore.appendMessage(context, ChatMessage.assistant("old answer"));
        stateStore.save(context, agentScope(context, "agent_meta"), "{\"step\":1}");

        service.chat(command("new question")).block();

        assertThat(recoveryService.agentScopeStatePresent(context)).isFalse();
        assertThat(agentRuntime.requests).hasSize(1);
        assertThat(agentRuntime.requests.get(0).messages())
                .extracting(ChatMessage::content)
                .containsExactly("old question", "old answer", "new question");
    }

    private ChatService chatService(AgentSessionRecoveryService recoveryService) {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        ProductionRuntimeProperties runtimeProperties = new ProductionRuntimeProperties();
        return new ChatService(
                contextFactory,
                sessionStore,
                agentRuntime,
                knowledgeService,
                RuntimeTelemetry.noop(),
                new BudgetLimiter(runtimeProperties, new RecordingBudgetCounterStore()),
                properties,
                new ModelConfigurationResolver(properties, runtimeProperties),
                recoveryService,
                new PromptInjectionGuard());
    }

    private static ChatCommand command(String message) {
        return new ChatCommand("tenant-a", "user-a", "agent-a", "session-a", message);
    }

    private static String agentScope(com.harnessagent.runtime.RuntimeContextScope context, String key) {
        return "agentscope:" + context.runtimeUserId() + ":" + context.runtimeSessionId() + ":" + key;
    }

    private static class RecordingBudgetCounterStore implements BudgetCounterStore {

        private final Map<String, BudgetCounter> counters = new LinkedHashMap<>();
        private final List<String> keys = new ArrayList<>();

        @Override
        public BudgetCounter increment(String key, long tokens) {
            keys.add(key);
            BudgetCounter current = counters.get(key);
            BudgetCounter next = new BudgetCounter(
                    key,
                    current == null ? 1 : current.requests() + 1,
                    current == null ? tokens : current.tokens() + tokens);
            counters.put(key, next);
            return next;
        }

        private List<String> keys() {
            return keys;
        }
    }

    private static class RecordingAgentRuntime implements AgentRuntime {

        protected final List<AgentRunRequest> requests = new ArrayList<>();

        @Override
        public Mono<AgentReply> complete(AgentRunRequest request) {
            requests.add(request);
            String lastMessage = request.messages().get(request.messages().size() - 1).content();
            return Mono.just(new AgentReply("answer:" + lastMessage));
        }

        @Override
        public Flux<AgentRuntimeEvent> stream(AgentRunRequest request) {
            requests.add(request);
            return Flux.just(
                    AgentRuntimeEvent.status("started"),
                    AgentRuntimeEvent.delta("chunk-1"),
                    AgentRuntimeEvent.delta("chunk-2"),
                    AgentRuntimeEvent.tool("search.docs", java.util.Map.of("toolStatus", "started")),
                    AgentRuntimeEvent.subagent("delegated to researcher", java.util.Map.of("subagentId", "researcher")),
                    AgentRuntimeEvent.done("completed"));
        }
    }

    private static class RecoveryAwareAgentRuntime extends RecordingAgentRuntime {

        private final AgentSessionRecoveryService recoveryService;
        private boolean sawPending;
        private com.harnessagent.runtime.RuntimeContextScope lastContext;

        private RecoveryAwareAgentRuntime(AgentSessionRecoveryService recoveryService) {
            this.recoveryService = recoveryService;
        }

        @Override
        public Mono<AgentReply> complete(AgentRunRequest request) {
            lastContext = request.context();
            sawPending = recoveryService.pendingExecution(request.context()).isPresent();
            return super.complete(request);
        }
    }

    private static class CancellableAgentRuntime extends RecordingAgentRuntime {

        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public Flux<AgentRuntimeEvent> stream(AgentRunRequest request) {
            requests.add(request);
            return Flux.<AgentRuntimeEvent>never().doOnCancel(() -> cancelled.set(true));
        }
    }

    private static class ToolResultAgentRuntime extends RecordingAgentRuntime {

        @Override
        public Flux<AgentRuntimeEvent> stream(AgentRunRequest request) {
            requests.add(request);
            return Flux.just(
                    AgentRuntimeEvent.delta("answer"),
                    AgentRuntimeEvent.tool("search.docs", java.util.Map.of(
                            "toolStatus", "result_started",
                            "toolCallId", "call-1",
                            "toolName", "search.docs")),
                    AgentRuntimeEvent.tool("{\"matches\":2}", java.util.Map.of(
                            "toolStatus", "result_delta",
                            "toolCallId", "call-1",
                            "toolName", "search.docs")),
                    AgentRuntimeEvent.tool("search.docs", java.util.Map.of(
                            "toolStatus", "result",
                            "toolCallId", "call-1",
                            "toolName", "search.docs",
                            "rawReference", "workspace://artifacts/search/raw.json",
                            "resultState", "SUCCEEDED")),
                    AgentRuntimeEvent.done("completed"));
        }
    }
}
