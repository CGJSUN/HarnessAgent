package com.harnessagent.agent.runtime;

import java.util.Map;

public record AgentRuntimeEvent(
        AgentRuntimeEventType type,
        String content,
        boolean terminal,
        AgentRuntimeChannel channel,
        Map<String, Object> attributes) {

    public AgentRuntimeEvent(AgentRuntimeEventType type, String content, boolean terminal) {
        this(type, content, terminal, defaultChannel(type, Map.of()), Map.of());
    }

    public AgentRuntimeEvent(
            AgentRuntimeEventType type,
            String content,
            boolean terminal,
            Map<String, Object> attributes) {
        this(type, content, terminal, defaultChannel(type, attributes), attributes);
    }

    public AgentRuntimeEvent {
        if (type == null) {
            throw new IllegalArgumentException("runtime event type is required");
        }
        channel = channel == null ? defaultChannel(type, attributes) : channel;
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

    public static AgentRuntimeEvent plan(String content, Map<String, ?> attributes) {
        return new AgentRuntimeEvent(
                AgentRuntimeEventType.STATUS,
                content,
                false,
                AgentRuntimeChannel.PLAN_UPDATE,
                copyAttributes(attributes));
    }

    public static AgentRuntimeEvent diagnostic(String content, Map<String, ?> attributes) {
        return new AgentRuntimeEvent(
                AgentRuntimeEventType.STATUS,
                content,
                false,
                AgentRuntimeChannel.DIAGNOSTIC,
                copyAttributes(attributes));
    }

    private static Map<String, Object> copyAttributes(Map<String, ?> attributes) {
        return attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    private static AgentRuntimeChannel defaultChannel(
            AgentRuntimeEventType type,
            Map<String, ?> attributes) {
        if (attributes != null) {
            Object requested = attributes.get("channel");
            if (requested != null) {
                try {
                    return AgentRuntimeChannel.valueOf(String.valueOf(requested).trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    // Fall through to the type-based default.
                }
            }
        }
        return switch (type) {
            case DELTA, DONE -> AgentRuntimeChannel.USER_VISIBLE;
            case TOOL -> AgentRuntimeChannel.TOOL_EVENT;
            case STATUS -> AgentRuntimeChannel.SYSTEM_NOTICE;
            case SUBAGENT, ERROR -> AgentRuntimeChannel.DIAGNOSTIC;
        };
    }
}
