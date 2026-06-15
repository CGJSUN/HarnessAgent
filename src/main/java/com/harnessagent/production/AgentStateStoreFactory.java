package com.harnessagent.production;

public interface AgentStateStoreFactory {

    AgentStateStore create(StateStorePlan plan);
}
