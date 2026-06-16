package com.harnessagent.api.response;

import com.harnessagent.agent.runtime.AgentRuntimeEventType;

public enum StreamEventKind {
    MODEL_STATUS,
    TEXT_DELTA,
    TOOL_EVENT,
    SUBAGENT_EVENT,
    ERROR,
    COMPLETION;

    public static StreamEventKind from(AgentRuntimeEventType type) {
        return switch (type) {
            case STATUS -> MODEL_STATUS;
            case DELTA -> TEXT_DELTA;
            case TOOL -> TOOL_EVENT;
            case SUBAGENT -> SUBAGENT_EVENT;
            case ERROR -> ERROR;
            case DONE -> COMPLETION;
        };
    }

    public static StreamEventKind fromTypeName(String type) {
        if (type == null) {
            return MODEL_STATUS;
        }
        return switch (type.toLowerCase()) {
            case "delta" -> TEXT_DELTA;
            case "tool" -> TOOL_EVENT;
            case "subagent" -> SUBAGENT_EVENT;
            case "error" -> ERROR;
            case "done" -> COMPLETION;
            default -> MODEL_STATUS;
        };
    }
}
