package com.harnessagent.production;

import com.harnessagent.runtime.RuntimeContextScope;
import java.util.Optional;
import java.util.Set;

public interface AgentStateStore {

    StateStorePlan plan();

    AgentStateEntry save(RuntimeContextScope context, String scope, String value);

    Optional<AgentStateEntry> load(RuntimeContextScope context, String scope);

    boolean delete(RuntimeContextScope context, String scope);

    boolean exists(RuntimeContextScope context, String sessionScope);

    boolean deleteSession(RuntimeContextScope context, String sessionScope);

    Set<String> listSessionScopes(RuntimeContextScope context);
}
