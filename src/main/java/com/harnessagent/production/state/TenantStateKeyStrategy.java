package com.harnessagent.production.state;

import com.harnessagent.runtime.RuntimeContextScope;

@Deprecated(forRemoval = true)
public class TenantStateKeyStrategy extends OwnerStateKeyStrategy {

    @Override
    public String key(RuntimeContextScope context, String isolationScope) {
        return legacyKey(context, isolationScope);
    }

    @Override
    public String scopePrefix(RuntimeContextScope context) {
        return legacyScopePrefix(context);
    }

    @Override
    public String sessionScopePrefix(RuntimeContextScope context, String sessionScope) {
        return legacySessionScopePrefix(context, sessionScope);
    }
}
