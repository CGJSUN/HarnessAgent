package com.harnessagent.api;

import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.session.ChatMessage;
import com.harnessagent.session.SessionStore;
import com.harnessagent.session.SessionSummary;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final RuntimeContextFactory runtimeContextFactory;
    private final SessionStore sessionStore;
    private final ApiIdentityResolver identityResolver;

    public SessionController(
            RuntimeContextFactory runtimeContextFactory,
            SessionStore sessionStore,
            ApiIdentityResolver identityResolver) {
        this.runtimeContextFactory = runtimeContextFactory;
        this.sessionStore = sessionStore;
        this.identityResolver = identityResolver;
    }

    @GetMapping("/sessions")
    public List<SessionSummary> listSessions(
            @RequestHeader Map<String, String> headers,
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestParam String agentId) {
        com.harnessagent.security.SecurityPrincipal principal =
                identityResolver.resolve(headers, tenantId, userId, null, null);
        RuntimeContextScope context = runtimeContextFactory.create(
                principal.tenantId(), principal.userId(), agentId, "_");
        return sessionStore.listSessions(context.tenantId(), context.userId(), context.agentId());
    }

    @GetMapping("/messages")
    public List<ChatMessage> listMessages(
            @RequestHeader Map<String, String> headers,
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestParam String agentId,
            @RequestParam String sessionId) {
        com.harnessagent.security.SecurityPrincipal principal =
                identityResolver.resolve(headers, tenantId, userId, null, null);
        RuntimeContextScope context = runtimeContextFactory.create(
                principal.tenantId(), principal.userId(), agentId, sessionId);
        return sessionStore.listMessages(context);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public DeleteSessionResponse deleteSession(
            @RequestHeader Map<String, String> headers,
            @PathVariable String sessionId,
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestParam String agentId) {
        com.harnessagent.security.SecurityPrincipal principal =
                identityResolver.resolve(headers, tenantId, userId, null, null);
        RuntimeContextScope context = runtimeContextFactory.create(
                principal.tenantId(), principal.userId(), agentId, sessionId);
        return new DeleteSessionResponse(sessionStore.deleteSession(context));
    }

    public record DeleteSessionResponse(boolean deleted) {
    }
}
