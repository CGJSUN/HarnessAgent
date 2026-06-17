package com.harnessagent.chat.application;

import com.harnessagent.agent.runtime.AgentReply;
import com.harnessagent.agent.runtime.AgentRunRequest;
import com.harnessagent.agent.runtime.AgentRuntime;
import com.harnessagent.agent.runtime.AgentRuntimeEvent;
import com.harnessagent.agent.runtime.AgentRuntimeEventType;
import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.model.ModelConfigurationResolver;
import com.harnessagent.model.ModelSelection;
import com.harnessagent.production.budget.BudgetDecision;
import com.harnessagent.production.budget.BudgetLimiter;
import com.harnessagent.production.budget.BudgetScope;
import com.harnessagent.production.infrastructure.InMemoryBudgetCounterStore;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.telemetry.RuntimeTelemetry;
import com.harnessagent.production.telemetry.TelemetryEventType;
import com.harnessagent.security.application.PromptInjectionGuard;
import com.harnessagent.security.application.SafeLogFields;
import com.harnessagent.security.domain.SecurityDecision;
import com.harnessagent.rag.retrieval.KnowledgeRetrievalResult;
import com.harnessagent.rag.application.KnowledgeService;
import com.harnessagent.rag.domain.RetrievalPrincipal;
import com.harnessagent.rag.domain.RetrievedKnowledge;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.persistence.SessionStore;
import com.harnessagent.workspace.application.ContextCompactionService;
import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.harnessagent.chat.domain.ChatCommand;
import com.harnessagent.chat.domain.ChatResult;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final RuntimeContextFactory runtimeContextFactory;
    private final SessionStore sessionStore;
    private final AgentRuntime agentRuntime;
    private final KnowledgeService knowledgeService;
    private final RuntimeTelemetry telemetry;
    private final BudgetLimiter budgetLimiter;
    private final HarnessAgentProperties properties;
    private final ModelConfigurationResolver modelConfigurationResolver;
    private final AgentSessionRecoveryService recoveryService;
    private final PromptInjectionGuard promptInjectionGuard;
    private final ContextCompactionService contextCompactionService;

    @Autowired
    public ChatService(
            RuntimeContextFactory runtimeContextFactory,
            SessionStore sessionStore,
            AgentRuntime agentRuntime,
            KnowledgeService knowledgeService,
            RuntimeTelemetry telemetry,
            BudgetLimiter budgetLimiter,
            HarnessAgentProperties properties,
            ModelConfigurationResolver modelConfigurationResolver,
            AgentSessionRecoveryService recoveryService,
            PromptInjectionGuard promptInjectionGuard,
            ContextCompactionService contextCompactionService) {
        this.runtimeContextFactory = runtimeContextFactory;
        this.sessionStore = sessionStore;
        this.agentRuntime = agentRuntime;
        this.knowledgeService = knowledgeService;
        this.telemetry = telemetry;
        this.budgetLimiter = budgetLimiter;
        this.properties = properties;
        this.modelConfigurationResolver = modelConfigurationResolver;
        this.recoveryService = recoveryService;
        this.promptInjectionGuard = promptInjectionGuard;
        this.contextCompactionService = contextCompactionService == null
                ? ContextCompactionService.disabled()
                : contextCompactionService;
    }

    public ChatService(
            RuntimeContextFactory runtimeContextFactory,
            SessionStore sessionStore,
            AgentRuntime agentRuntime,
            KnowledgeService knowledgeService,
            RuntimeTelemetry telemetry,
            BudgetLimiter budgetLimiter,
            HarnessAgentProperties properties,
            ModelConfigurationResolver modelConfigurationResolver,
            AgentSessionRecoveryService recoveryService,
            PromptInjectionGuard promptInjectionGuard) {
        this(
                runtimeContextFactory,
                sessionStore,
                agentRuntime,
                knowledgeService,
                telemetry,
                budgetLimiter,
                properties,
                modelConfigurationResolver,
                recoveryService,
                promptInjectionGuard,
                ContextCompactionService.disabled());
    }

    public ChatService(
            RuntimeContextFactory runtimeContextFactory,
            SessionStore sessionStore,
            AgentRuntime agentRuntime,
            KnowledgeService knowledgeService) {
        this(runtimeContextFactory, sessionStore, agentRuntime, knowledgeService, new HarnessAgentProperties(),
                new ProductionRuntimeProperties());
    }

    private ChatService(
            RuntimeContextFactory runtimeContextFactory,
            SessionStore sessionStore,
            AgentRuntime agentRuntime,
            KnowledgeService knowledgeService,
            HarnessAgentProperties properties,
            ProductionRuntimeProperties runtimeProperties) {
        this(
                runtimeContextFactory,
                sessionStore,
                agentRuntime,
                knowledgeService,
                RuntimeTelemetry.noop(),
                new BudgetLimiter(runtimeProperties, new InMemoryBudgetCounterStore()),
                properties,
                new ModelConfigurationResolver(properties, runtimeProperties),
                AgentSessionRecoveryService.noop(sessionStore),
                new PromptInjectionGuard());
    }

    public Mono<ChatResult> chat(ChatCommand command) {
        Instant startedAt = Instant.now();
        RuntimeContextScope context = context(command);
        ChatCommand effective = effectiveCommand(command, context);
        // This order is intentional: establish runtime isolation before safety, budget, persistence, RAG, and model use.
        enforcePromptSafety(effective);
        enforceBudget(effective);
        ChatMessage userMessage = ChatMessage.user(effective.message());
        sessionStore.appendMessage(context, userMessage);

        KnowledgeRetrievalResult knowledge = retrieveKnowledgeIfEnabled(effective);
        recordRagTelemetry(effective, knowledge);
        if (knowledge != null && !knowledge.answered()) {
            log.warn(
                    "chat rag no_answer tenantId={} agentId={} userHash={} sessionHash={} reason={}",
                    effective.tenantId(),
                    effective.agentId(),
                    SafeLogFields.user(effective.userId()),
                    SafeLogFields.session(effective.sessionId()),
                    SafeLogFields.reasonCode(knowledge.message()));
            ChatMessage assistant = ChatMessage.assistant(knowledge.message());
            sessionStore.appendMessage(context, assistant);
            return Mono.just(ChatResult.noAnswer(assistant, context))
                    .doOnSuccess(result -> recordChatTelemetry(effective, startedAt, "knowledge_no_answer"));
        }

        List<ChatMessage> messages = messagesForRuntime(context, messagesForAgent(context, effective, knowledge));
        recoveryService.markPending(context, "complete", userMessage.id());
        return agentRuntime.complete(new AgentRunRequest(context, messages))
                .map(reply -> persistAssistantMessage(context, reply, knowledge))
                .doOnSuccess(result -> recordChatTelemetry(effective, startedAt, "succeeded"))
                .doOnError(error -> {
                    log.error(
                            "chat model failed tenantId={} agentId={} userHash={} sessionHash={} errorType={}",
                            effective.tenantId(),
                            effective.agentId(),
                            SafeLogFields.user(effective.userId()),
                            SafeLogFields.session(effective.sessionId()),
                            error.getClass().getSimpleName());
                    recordChatTelemetry(effective, startedAt, "failed");
                })
                .doFinally(ignored -> recoveryService.clearPending(context));
    }

    public Flux<AgentRuntimeEvent> stream(ChatCommand command) {
        Instant startedAt = Instant.now();
        RuntimeContextScope context = context(command);
        ChatCommand effective = effectiveCommand(command, context);
        // Streaming follows the same governance order as non-streaming; SSE serialization happens only after checks pass.
        enforcePromptSafety(effective);
        enforceBudget(effective);
        ChatMessage userMessage = ChatMessage.user(effective.message());
        sessionStore.appendMessage(context, userMessage);

        KnowledgeRetrievalResult knowledge = retrieveKnowledgeIfEnabled(effective);
        recordRagTelemetry(effective, knowledge);
        if (knowledge != null && !knowledge.answered()) {
            log.warn(
                    "chat stream rag no_answer tenantId={} agentId={} userHash={} sessionHash={} reason={}",
                    effective.tenantId(),
                    effective.agentId(),
                    SafeLogFields.user(effective.userId()),
                    SafeLogFields.session(effective.sessionId()),
                    SafeLogFields.reasonCode(knowledge.message()));
            sessionStore.appendMessage(context, ChatMessage.assistant(knowledge.message()));
            return Flux.just(
                    AgentRuntimeEvent.status("knowledge_no_answer", Map.of("noAnswerReason", knowledge.message())),
                    AgentRuntimeEvent.delta(knowledge.message()),
                    AgentRuntimeEvent.done("completed", Map.of("noAnswerReason", knowledge.message())))
                    .doOnComplete(() -> recordChatTelemetry(effective, startedAt, "knowledge_no_answer"));
        }

        List<ChatMessage> messages = messagesForRuntime(context, messagesForAgent(context, effective, knowledge));
        StringBuilder assistantContent = new StringBuilder();
        recoveryService.markPending(context, "stream", userMessage.id());
        return agentRuntime.stream(new AgentRunRequest(context, messages))
                .doOnNext(event -> {
                    if (event.type() == AgentRuntimeEventType.DELTA) {
                        assistantContent.append(event.content());
                    }
                })
                .map(event -> {
                    if (event.type() == AgentRuntimeEventType.DONE
                            && knowledge != null
                            && knowledge.answered()) {
                        return AgentRuntimeEvent.done(
                                event.content(),
                                Map.of("citations", knowledge.citations()));
                    }
                    return event;
                })
                .doOnComplete(() -> {
                    if (!assistantContent.isEmpty()) {
                        sessionStore.appendMessage(
                                context, ChatMessage.assistant(assistantContent.toString()));
                    }
                })
                .doOnComplete(() -> recordChatTelemetry(effective, startedAt, "succeeded"))
                .doOnError(error -> {
                    log.error(
                            "chat stream failed tenantId={} agentId={} userHash={} sessionHash={} errorType={}",
                            effective.tenantId(),
                            effective.agentId(),
                            SafeLogFields.user(effective.userId()),
                            SafeLogFields.session(effective.sessionId()),
                            error.getClass().getSimpleName());
                    recordChatTelemetry(effective, startedAt, "failed");
                })
                .doFinally(ignored -> recoveryService.clearPending(context));
    }

    private ChatResult persistAssistantMessage(
            RuntimeContextScope context, AgentReply reply, KnowledgeRetrievalResult knowledge) {
        ChatMessage assistant = ChatMessage.assistant(reply.content());
        sessionStore.appendMessage(context, assistant);
        if (knowledge != null && knowledge.answered()) {
            return ChatResult.knowledgeBacked(
                    assistant,
                    context,
                    knowledge.citations());
        }
        return ChatResult.plain(assistant, context);
    }

    private List<ChatMessage> messagesForAgent(
            RuntimeContextScope context, ChatCommand command, KnowledgeRetrievalResult knowledge) {
        List<ChatMessage> messages = new ArrayList<>(sessionStore.listMessages(context));
        if (knowledge == null || !knowledge.answered()) {
            return messages;
        }
        if (!messages.isEmpty()) {
            messages.remove(messages.size() - 1);
        }
        // Accessible RAG evidence is injected as a constrained user prompt; no accessible evidence must short-circuit above.
        messages.add(ChatMessage.user(buildKnowledgePrompt(command.message(), knowledge.results())));
        return messages;
    }

    private List<ChatMessage> messagesForRuntime(RuntimeContextScope context, List<ChatMessage> messages) {
        if (messages.isEmpty() || !recoveryService.agentScopeStatePresent(context)) {
            return contextCompactionService.compactIfNeeded(context, messages);
        }
        return List.of(messages.get(messages.size() - 1));
    }

    private KnowledgeRetrievalResult retrieveKnowledgeIfEnabled(ChatCommand command) {
        if (!command.knowledgeEnabled()) {
            return null;
        }
        RetrievalPrincipal principal = new RetrievalPrincipal(
                command.tenantId(),
                command.userId(),
                safeSet(command.departments()),
                safeSet(command.roles()));
        return knowledgeService.retrieve(principal, command.message(), command.knowledgeLimit());
    }

    private RuntimeContextScope context(ChatCommand command) {
        requireMessage(command.message());
        return runtimeContextFactory.create(
                command.tenantId(), command.userId(), command.agentId(), command.sessionId());
    }

    private static ChatCommand effectiveCommand(ChatCommand command, RuntimeContextScope context) {
        return new ChatCommand(
                context.tenantId(),
                context.userId(),
                context.agentId(),
                context.sessionId(),
                command.message(),
                command.knowledgeEnabled(),
                safeSet(command.departments()),
                safeSet(command.roles()),
                command.knowledgeLimit());
    }

    private void enforceBudget(ChatCommand command) {
        ModelSelection selection = modelConfigurationResolver.resolve(command.agentId());
        BudgetDecision decision = budgetLimiter.tryConsume(new BudgetScope(
                command.tenantId(),
                command.userId(),
                command.agentId(),
                firstNonBlank(selection.providerId(), properties.getDefaultProvider(), "default")),
                estimateTokens(command.message()),
                selection.budgetLimit());
        telemetry.record(
                TelemetryEventType.TOKEN,
                command.tenantId(),
                command.userId(),
                command.agentId(),
                "budget",
                Duration.ZERO,
                Map.of(
                        "allowed", decision.allowed(),
                        "reason", decision.reason(),
                        "usedRequests", decision.usedRequests(),
                        "usedTokens", decision.usedTokens()));
        if (!decision.allowed()) {
            log.warn(
                    "chat budget rejected tenantId={} agentId={} userHash={} reason={} usedRequests={} usedTokens={}",
                    command.tenantId(),
                    command.agentId(),
                    SafeLogFields.user(command.userId()),
                    SafeLogFields.reasonCode(decision.reason()),
                    decision.usedRequests(),
                    decision.usedTokens());
            throw new IllegalStateException("Budget exceeded: " + decision.reason());
        }
    }

    private void enforcePromptSafety(ChatCommand command) {
        SecurityDecision decision = promptInjectionGuard.inspectText(command.message());
        if (!decision.allowed()) {
            log.warn(
                    "chat prompt safety rejected tenantId={} agentId={} userHash={} sessionHash={} reason={}",
                    command.tenantId(),
                    command.agentId(),
                    SafeLogFields.user(command.userId()),
                    SafeLogFields.session(command.sessionId()),
                    SafeLogFields.reasonCode(decision.reason()));
            throw new IllegalStateException("Unsafe prompt rejected: " + decision.reason());
        }
    }

    private void recordRagTelemetry(ChatCommand command, KnowledgeRetrievalResult knowledge) {
        if (knowledge == null) {
            return;
        }
        telemetry.record(
                TelemetryEventType.RAG,
                command.tenantId(),
                command.userId(),
                command.agentId(),
                "knowledge-service",
                Duration.ZERO,
                Map.of(
                        "answered", knowledge.answered(),
                        "resultCount", knowledge.results().size(),
                        "citationCount", knowledge.citations().size()));
    }

    private void recordChatTelemetry(ChatCommand command, Instant startedAt, String status) {
        telemetry.record(
                TelemetryEventType.API,
                command.tenantId(),
                command.userId(),
                command.agentId(),
                "chat",
                Duration.between(startedAt, Instant.now()),
                Map.of("status", status));
    }

    private static String buildKnowledgePrompt(String userMessage, List<RetrievedKnowledge> evidence) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请仅基于以下可访问知识回答用户问题。若证据不足，请说明无法从当前可用知识中确定答案。\n\n");
        for (int index = 0; index < evidence.size(); index++) {
            RetrievedKnowledge item = evidence.get(index);
            prompt.append("[证据 ").append(index + 1).append("] ")
                    .append(item.citation().title())
                    .append(" / version=")
                    .append(item.citation().version())
                    .append(" / chunk=")
                    .append(item.citation().chunkIndex())
                    .append("\n")
                    .append(item.content())
                    .append("\n\n");
        }
        prompt.append("用户问题：").append(userMessage);
        return prompt.toString();
    }

    private static void requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
    }

    private static Set<String> safeSet(Set<String> input) {
        return input == null ? Set.of() : input;
    }

    private static long estimateTokens(String message) {
        if (message == null || message.isBlank()) {
            return 0;
        }
        return Math.max(1, (long) Math.ceil(message.length() / 4.0));
    }

    private static String firstNonBlank(String first, String... rest) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (rest == null) {
            return null;
        }
        for (String value : rest) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
