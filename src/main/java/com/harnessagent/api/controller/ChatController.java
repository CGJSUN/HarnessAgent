package com.harnessagent.api.controller;

import com.harnessagent.api.ApiIdentityResolver;
import com.harnessagent.api.request.ChatRequest;
import com.harnessagent.api.response.ChatResponse;
import com.harnessagent.api.response.StreamEventResponse;
import com.harnessagent.api.response.StreamEventKind;
import com.harnessagent.agent.runtime.AgentRuntimeEvent;
import com.harnessagent.chat.domain.ChatCommand;
import com.harnessagent.chat.domain.ChatResult;
import com.harnessagent.chat.application.ChatService;
import com.harnessagent.rag.domain.KnowledgeCitation;
import com.harnessagent.security.domain.SecurityPrincipal;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.Disposable;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final long STREAM_TIMEOUT_MILLIS = Duration.ofMinutes(5).toMillis();

    private final ChatService chatService;
    private final ApiIdentityResolver identityResolver;

    public ChatController(ChatService chatService, ApiIdentityResolver identityResolver) {
        this.chatService = chatService;
        this.identityResolver = identityResolver;
    }

    @PostMapping
    public ChatResponse chat(
            @RequestHeader Map<String, String> headers,
            @RequestBody ChatRequest request) {
        ChatResult result = chatService.chat(toCommand(headers, request)).block();
        return new ChatResponse(
                result.messageId(),
                result.sessionId(),
                result.message(),
                result.contentBlocks(),
                result.executionSummary(),
                result.runtimeUserId(),
                result.runtimeSessionId(),
                result.knowledgeBacked(),
                result.noAnswerReason(),
                result.citations());
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader Map<String, String> headers,
            @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        Disposable subscription = Flux.defer(() -> chatService.stream(toCommand(headers, request)))
                .onErrorResume(error -> Flux.just(AgentRuntimeEvent.error(errorMessage(error))))
                .subscribe(
                        event -> send(emitter, event),
                        error -> {
                            sendError(emitter, error);
                            emitter.complete();
                        },
                        emitter::complete);
        emitter.onTimeout(subscription::dispose);
        emitter.onCompletion(subscription::dispose);
        emitter.onError(ignored -> subscription.dispose());
        return emitter;
    }

    private ChatCommand toCommand(Map<String, String> headers, ChatRequest request) {
        SecurityPrincipal principal = identityResolver.resolve(
                headers,
                request.tenantId(),
                request.userId(),
                request.roles(),
                request.departments());
        return new ChatCommand(
                principal.tenantId(),
                principal.userId(),
                request.agentId(),
                request.sessionId(),
                request.message(),
                request.knowledgeEnabled(),
                principal.departments(),
                principal.roles(),
                request.knowledgeLimit());
    }

    private static void send(SseEmitter emitter, AgentRuntimeEvent event) {
        try {
            String type = event.type().name().toLowerCase();
            emitter.send(SseEmitter.event()
                    .name(type)
                    .data(new StreamEventResponse(
                            type,
                            StreamEventKind.from(event.type()).name(),
                            event.channel().name(),
                            event.content(),
                            event.terminal(),
                            (String) event.attributes().get("noAnswerReason"),
                            citations(event.attributes().get("citations")),
                            event.attributes())));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    private static void sendError(SseEmitter emitter, Throwable error) {
        send(emitter, AgentRuntimeEvent.error(errorMessage(error)));
    }

    private static String errorMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "stream failed";
        }
        return error.getMessage();
    }

    @SuppressWarnings("unchecked")
    private static List<KnowledgeCitation> citations(Object value) {
        if (value instanceof List<?> list && list.stream().allMatch(KnowledgeCitation.class::isInstance)) {
            return (List<KnowledgeCitation>) list;
        }
        return List.of();
    }
}
