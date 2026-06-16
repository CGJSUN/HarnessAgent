package com.harnessagent.agent.runtime;

import java.util.Map;

public record AgentRuntimeEvent(
        AgentRuntimeEventType type,
        String content,
        boolean terminal,
        Map<String, Object> attributes) {

    public AgentRuntimeEvent(AgentRuntimeEventType type, String content, boolean terminal) {
        this(type, content, terminal, Map.of());
    }

    public AgentRuntimeEvent {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static AgentRuntimeEvent status(String content) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.STATUS, content, false);
    }

    public static AgentRuntimeEvent status(String content, Map<String, Object> attributes) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.STATUS, content, false, attributes);
    }

    public static AgentRuntimeEvent delta(String content) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.DELTA, content, false);
    }

    public static AgentRuntimeEvent delta(String content, Map<String, ?> attributes) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.DELTA, content, false, copyAttributes(attributes));
    }

    public static AgentRuntimeEvent tool(String content, Map<String, ?> attributes) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.TOOL, content, false, copyAttributes(attributes));
    }

    public static AgentRuntimeEvent subagent(String content, Map<String, ?> attributes) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.SUBAGENT, content, false, copyAttributes(attributes));
    }

    public static AgentRuntimeEvent error(String content) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.ERROR, content, true);
    }

    public static AgentRuntimeEvent done(String content) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.DONE, content, true);
    }

    public static AgentRuntimeEvent done(String content, Map<String, Object> attributes) {
        return new AgentRuntimeEvent(AgentRuntimeEventType.DONE, content, true, attributes);
    }

    private static Map<String, Object> copyAttributes(Map<String, ?> attributes) {
        return attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
