package com.harnessagent.production.state;

public interface AgentStateStoreFactory {

    AgentStateStore create(StateStorePlan plan);
}
