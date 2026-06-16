package com.harnessagent.production.state;

import com.harnessagent.runtime.RuntimeContextScope;
import org.springframework.stereotype.Component;

@Component
public class TenantStateKeyStrategy {

    public String key(RuntimeContextScope context, String isolationScope) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        String scope = isolationScope == null || isolationScope.isBlank() ? "default" : isolationScope.trim();
        return String.join(":",
                "tenant", context.tenantId(),
                "user", context.userId(),
                "agent", context.agentId(),
                "session", context.sessionId(),
                "scope", scope);
    }
}
