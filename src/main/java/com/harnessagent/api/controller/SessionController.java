package com.harnessagent.api.controller;

import com.harnessagent.api.ApiIdentityResolver;
import com.harnessagent.api.response.DeleteSessionResponse;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.persistence.SessionStore;
import com.harnessagent.session.domain.SessionSummary;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.harnessagent.security.domain.OwnerPrincipal;

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
            @RequestParam String ownerId,
            @RequestParam String agentId) {
        OwnerPrincipal principal = identityResolver.resolve(headers, ownerId);
        RuntimeContextScope context = runtimeContextFactory.createPersonal(principal.ownerId(), agentId, "_");
        return sessionStore.listSessions(context.ownerScopeId(), context.ownerId(), context.agentId());
    }

    @GetMapping("/messages")
    public List<ChatMessage> listMessages(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId,
            @RequestParam String agentId,
            @RequestParam String sessionId) {
        OwnerPrincipal principal = identityResolver.resolve(headers, ownerId);
        RuntimeContextScope context = runtimeContextFactory.createPersonal(principal.ownerId(), agentId, sessionId);
        return sessionStore.listMessages(context);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public DeleteSessionResponse deleteSession(
            @RequestHeader Map<String, String> headers,
            @PathVariable String sessionId,
            @RequestParam String ownerId,
            @RequestParam String agentId) {
        OwnerPrincipal principal = identityResolver.resolve(headers, ownerId);
        RuntimeContextScope context = runtimeContextFactory.createPersonal(principal.ownerId(), agentId, sessionId);
        return new DeleteSessionResponse(sessionStore.deleteSession(context));
    }
}
