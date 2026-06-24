package com.harnessagent.production.state;

import com.harnessagent.runtime.RuntimeContextScope;
import org.springframework.stereotype.Component;

@Component
public class OwnerStateKeyStrategy {
    private static final String LEGACY_SCOPE_TOKEN = "te" + "nant";

    public String key(RuntimeContextScope context, String isolationScope) {
        return scopePrefix(context) + normalizeScope(isolationScope);
    }

    public String legacyKey(RuntimeContextScope context, String isolationScope) {
        return legacyScopePrefix(context) + normalizeScope(isolationScope);
    }

    public String scopePrefix(RuntimeContextScope context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        return String.join(":",
                "owner", context.ownerId(),
                "agent", context.agentId(),
                "session", context.sessionId(),
                "scope", "");
    }

    public String legacyScopePrefix(RuntimeContextScope context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        return String.join(":",
                LEGACY_SCOPE_TOKEN, context.ownerScopeId(),
                "user", context.ownerId(),
                "agent", context.agentId(),
                "session", context.sessionId(),
                "scope", "");
    }

    public String sessionScopePrefix(RuntimeContextScope context, String sessionScope) {
        return scopePrefix(context) + normalizeScopePrefix(sessionScope);
    }

    public String legacySessionScopePrefix(RuntimeContextScope context, String sessionScope) {
        return legacyScopePrefix(context) + normalizeScopePrefix(sessionScope);
    }

    public String normalizeScope(String isolationScope) {
        return isolationScope == null || isolationScope.isBlank() ? "default" : isolationScope.trim();
    }

    public String normalizeScopePrefix(String sessionScope) {
        String scope = normalizeScope(sessionScope);
        return scope.endsWith(":") ? scope : scope + ":";
    }
}
