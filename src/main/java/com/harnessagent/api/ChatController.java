package com.harnessagent.api;

import com.harnessagent.agent.AgentRuntimeEvent;
import com.harnessagent.chat.ChatCommand;
import com.harnessagent.chat.ChatResult;
import com.harnessagent.chat.ChatService;
import com.harnessagent.security.SecurityPrincipal;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
                result.message(),
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
        Disposable subscription = chatService.stream(toCommand(headers, request))
                .subscribe(
                        event -> send(emitter, event),
                        emitter::completeWithError,
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
            emitter.send(SseEmitter.event()
                    .name(event.type().name().toLowerCase())
                    .data(new StreamEventResponse(
                            event.type().name().toLowerCase(), event.content(), event.terminal())));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }
}
