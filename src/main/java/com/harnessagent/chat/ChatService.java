package com.harnessagent.chat;

import com.harnessagent.agent.AgentReply;
import com.harnessagent.agent.AgentRunRequest;
import com.harnessagent.agent.AgentRuntime;
import com.harnessagent.agent.AgentRuntimeEvent;
import com.harnessagent.agent.AgentRuntimeEventType;
import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.production.BudgetDecision;
import com.harnessagent.production.BudgetLimiter;
import com.harnessagent.production.BudgetScope;
import com.harnessagent.production.ProductionRuntimeProperties;
import com.harnessagent.production.RuntimeTelemetry;
import com.harnessagent.production.TelemetryEventType;
import com.harnessagent.security.PromptInjectionGuard;
import com.harnessagent.security.SecurityDecision;
import com.harnessagent.rag.KnowledgeRetrievalResult;
import com.harnessagent.rag.KnowledgeService;
import com.harnessagent.rag.RetrievalPrincipal;
import com.harnessagent.rag.RetrievedKnowledge;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.session.ChatMessage;
import com.harnessagent.session.SessionStore;
import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ChatService {

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
                new BudgetLimiter(new ProductionRuntimeProperties()),
                new HarnessAgentProperties(),
                new PromptInjectionGuard());
    }

    public Mono<ChatResult> chat(ChatCommand command) {
        Instant startedAt = Instant.now();
        RuntimeContextScope context = context(command);
        enforcePromptSafety(command.message());
        enforceBudget(command);
        ChatMessage userMessage = ChatMessage.user(command.message());
        sessionStore.appendMessage(context, userMessage);

        KnowledgeRetrievalResult knowledge = retrieveKnowledgeIfEnabled(command);
        recordRagTelemetry(command, knowledge);
        if (knowledge != null && !knowledge.answered()) {
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
                .doOnError(error -> recordChatTelemetry(command, startedAt, "failed"));
    }

    public Flux<AgentRuntimeEvent> stream(ChatCommand command) {
        Instant startedAt = Instant.now();
        RuntimeContextScope context = context(command);
        enforcePromptSafety(command.message());
        enforceBudget(command);
        ChatMessage userMessage = ChatMessage.user(command.message());
        sessionStore.appendMessage(context, userMessage);

        KnowledgeRetrievalResult knowledge = retrieveKnowledgeIfEnabled(command);
        recordRagTelemetry(command, knowledge);
        if (knowledge != null && !knowledge.answered()) {
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
                .doOnError(error -> recordChatTelemetry(command, startedAt, "failed"));
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
            throw new IllegalStateException("Budget exceeded: " + decision.reason());
        }
    }

    private void enforcePromptSafety(String message) {
        SecurityDecision decision = promptInjectionGuard.inspectText(message);
        if (!decision.allowed()) {
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
