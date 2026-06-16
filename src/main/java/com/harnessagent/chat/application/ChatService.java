package com.harnessagent.chat.application;

import com.harnessagent.agent.runtime.AgentReply;
import com.harnessagent.agent.runtime.AgentRunRequest;
import com.harnessagent.agent.runtime.AgentRuntime;
import com.harnessagent.agent.runtime.AgentRuntimeEvent;
import com.harnessagent.agent.runtime.AgentRuntimeEventType;
import com.harnessagent.config.HarnessAgentProperties;
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
    private final PromptInjectionGuard promptInjectionGuard;

    @Autowired
    public ChatService(
            RuntimeContextFactory runtimeContextFactory,
            SessionStore sessionStore,
            AgentRuntime agentRuntime,
            KnowledgeService knowledgeService,
            RuntimeTelemetry telemetry,
            BudgetLimiter budgetLimiter,
            HarnessAgentProperties properties,
            PromptInjectionGuard promptInjectionGuard) {
        this.runtimeContextFactory = runtimeContextFactory;
        this.sessionStore = sessionStore;
        this.agentRuntime = agentRuntime;
        this.knowledgeService = knowledgeService;
        this.telemetry = telemetry;
        this.budgetLimiter = budgetLimiter;
        this.properties = properties;
        this.promptInjectionGuard = promptInjectionGuard;
    }

    public ChatService(
            RuntimeContextFactory runtimeContextFactory,
            SessionStore sessionStore,
            AgentRuntime agentRuntime,
            KnowledgeService knowledgeService) {
        this(
                runtimeContextFactory,
                sessionStore,
                agentRuntime,
                knowledgeService,
                RuntimeTelemetry.noop(),
                new BudgetLimiter(new ProductionRuntimeProperties(), new InMemoryBudgetCounterStore()),
                new HarnessAgentProperties(),
                new PromptInjectionGuard());
    }

    public Mono<ChatResult> chat(ChatCommand command) {
        Instant startedAt = Instant.now();
        RuntimeContextScope context = context(command);
        // This order is intentional: establish runtime isolation before safety, budget, persistence, RAG, and model use.
        enforcePromptSafety(command);
        enforceBudget(command);
        ChatMessage userMessage = ChatMessage.user(command.message());
        sessionStore.appendMessage(context, userMessage);

        KnowledgeRetrievalResult knowledge = retrieveKnowledgeIfEnabled(command);
        recordRagTelemetry(command, knowledge);
        if (knowledge != null && !knowledge.answered()) {
            log.warn(
                    "chat rag no_answer tenantId={} agentId={} userHash={} sessionHash={} reason={}",
                    command.tenantId(),
                    command.agentId(),
                    SafeLogFields.user(command.userId()),
                    SafeLogFields.session(command.sessionId()),
                    SafeLogFields.reasonCode(knowledge.message()));
            ChatMessage assistant = ChatMessage.assistant(knowledge.message());
            sessionStore.appendMessage(context, assistant);
            return Mono.just(ChatResult.noAnswer(
                            assistant.content(), context.runtimeUserId(), context.runtimeSessionId()))
                    .doOnSuccess(result -> recordChatTelemetry(command, startedAt, "knowledge_no_answer"));
        }

        List<ChatMessage> messages = messagesForAgent(context, command, knowledge);
        return agentRuntime.complete(new AgentRunRequest(context, messages))
                .map(reply -> persistAssistantMessage(context, reply, knowledge))
                .doOnSuccess(result -> recordChatTelemetry(command, startedAt, "succeeded"))
                .doOnError(error -> {
                    log.error(
                            "chat model failed tenantId={} agentId={} userHash={} sessionHash={} errorType={}",
                            command.tenantId(),
                            command.agentId(),
                            SafeLogFields.user(command.userId()),
                            SafeLogFields.session(command.sessionId()),
                            error.getClass().getSimpleName());
                    recordChatTelemetry(command, startedAt, "failed");
                });
    }

    public Flux<AgentRuntimeEvent> stream(ChatCommand command) {
        Instant startedAt = Instant.now();
        RuntimeContextScope context = context(command);
        // Streaming follows the same governance order as non-streaming; SSE serialization happens only after checks pass.
        enforcePromptSafety(command);
        enforceBudget(command);
        ChatMessage userMessage = ChatMessage.user(command.message());
        sessionStore.appendMessage(context, userMessage);

        KnowledgeRetrievalResult knowledge = retrieveKnowledgeIfEnabled(command);
        recordRagTelemetry(command, knowledge);
        if (knowledge != null && !knowledge.answered()) {
            log.warn(
                    "chat stream rag no_answer tenantId={} agentId={} userHash={} sessionHash={} reason={}",
                    command.tenantId(),
                    command.agentId(),
                    SafeLogFields.user(command.userId()),
                    SafeLogFields.session(command.sessionId()),
                    SafeLogFields.reasonCode(knowledge.message()));
            sessionStore.appendMessage(context, ChatMessage.assistant(knowledge.message()));
            return Flux.just(
                    AgentRuntimeEvent.status("knowledge_no_answer", Map.of("noAnswerReason", knowledge.message())),
                    AgentRuntimeEvent.delta(knowledge.message()),
                    AgentRuntimeEvent.done("completed", Map.of("noAnswerReason", knowledge.message())))
                    .doOnComplete(() -> recordChatTelemetry(command, startedAt, "knowledge_no_answer"));
        }

        List<ChatMessage> messages = messagesForAgent(context, command, knowledge);
        StringBuilder assistantContent = new StringBuilder();
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
                .doOnComplete(() -> recordChatTelemetry(command, startedAt, "succeeded"))
                .doOnError(error -> {
                    log.error(
                            "chat stream failed tenantId={} agentId={} userHash={} sessionHash={} errorType={}",
                            command.tenantId(),
                            command.agentId(),
                            SafeLogFields.user(command.userId()),
                            SafeLogFields.session(command.sessionId()),
                            error.getClass().getSimpleName());
                    recordChatTelemetry(command, startedAt, "failed");
                });
    }

    private ChatResult persistAssistantMessage(
            RuntimeContextScope context, AgentReply reply, KnowledgeRetrievalResult knowledge) {
        ChatMessage assistant = ChatMessage.assistant(reply.content());
        sessionStore.appendMessage(context, assistant);
        if (knowledge != null && knowledge.answered()) {
            return ChatResult.knowledgeBacked(
                    assistant.content(),
                    context.runtimeUserId(),
                    context.runtimeSessionId(),
                    knowledge.citations());
        }
        return ChatResult.plain(assistant.content(), context.runtimeUserId(), context.runtimeSessionId());
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

    private void enforceBudget(ChatCommand command) {
        BudgetDecision decision = budgetLimiter.tryConsume(new BudgetScope(
                command.tenantId(),
                command.userId(),
                command.agentId(),
                firstNonBlank(properties.getDefaultProvider(), "default")), estimateTokens(command.message()));
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

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }
}
