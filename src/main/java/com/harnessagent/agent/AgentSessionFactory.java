package com.harnessagent.agent;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.production.ProductionRuntimeValidator;
import com.harnessagent.production.StateStorePlan;
import com.harnessagent.production.TenantStateKeyStrategy;
import com.harnessagent.runtime.RuntimeContextScope;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.session.JsonSession;
import io.agentscope.core.session.SessionManager;
import org.springframework.stereotype.Component;

@Component
public class AgentSessionFactory {

    private final HarnessAgentProperties properties;
    private final ProductionRuntimeValidator runtimeValidator;
    private final TenantStateKeyStrategy stateKeyStrategy;

    public AgentSessionFactory(
            HarnessAgentProperties properties,
            ProductionRuntimeValidator runtimeValidator,
            TenantStateKeyStrategy stateKeyStrategy) {
        this.properties = properties;
        this.runtimeValidator = runtimeValidator;
        this.stateKeyStrategy = stateKeyStrategy;
    }

    public SessionManager create(RuntimeContextScope context, ReActAgent agent) {
        StateStorePlan plan = runtimeValidator.stateStorePlan();
        JsonSession session = new JsonSession(properties.getState().getLocalDirectory());
        return SessionManager.forSessionId(stateKeyStrategy.key(context, "agentscope:" + plan.type().name()))
                .withSession(session)
                .addComponent(agent);
    }
}
