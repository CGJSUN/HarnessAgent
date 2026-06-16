package com.harnessagent.agent.application;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.production.infrastructure.AgentScopeStateStoreAdapter;
import com.harnessagent.production.state.AgentStateStore;
import com.harnessagent.production.state.AgentStateStoreFactory;
import com.harnessagent.production.health.ProductionRuntimeValidator;
import com.harnessagent.production.state.StateStorePlan;
import com.harnessagent.runtime.RuntimeContextScope;
import io.agentscope.core.agent.RuntimeContext;
import org.springframework.stereotype.Component;

@Component
public class AgentSessionFactory {

    private final HarnessAgentProperties properties;
    private final ProductionRuntimeValidator runtimeValidator;
    private final AgentStateStoreFactory stateStoreFactory;

    public AgentSessionFactory(
            HarnessAgentProperties properties,
            ProductionRuntimeValidator runtimeValidator,
            AgentStateStoreFactory stateStoreFactory) {
        this.properties = properties;
        this.runtimeValidator = runtimeValidator;
        this.stateStoreFactory = stateStoreFactory;
    }

    public io.agentscope.core.state.AgentStateStore stateStore(RuntimeContextScope context) {
        StateStorePlan plan = runtimeValidator.stateStorePlan();
        AgentStateStore store = stateStoreFactory.create(plan);
        return new AgentScopeStateStoreAdapter(context, store);
    }

    public RuntimeContext runtimeContext(RuntimeContextScope context) {
        return RuntimeContext.builder()
                .userId(context.runtimeUserId())
                .sessionId(context.runtimeSessionId())
                .build();
    }
}
