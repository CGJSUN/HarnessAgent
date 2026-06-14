package com.harnessagent.agent;

public record AgentRuntimeEvent(AgentRuntimeEventType type, String content, boolean terminal) {

    public static AgentRuntimeEvent status(String content) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.STATUS, content, false);
    }

    public static AgentRuntimeEvent delta(String content) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.DELTA, content, false);
    }

    public static AgentRuntimeEvent error(String content) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.ERROR, content, true);
    }

    public static AgentRuntimeEvent done(String content) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.DONE, content, true);
    }
}
