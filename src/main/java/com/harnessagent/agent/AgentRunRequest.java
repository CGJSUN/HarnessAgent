package com.harnessagent.agent;

import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.session.ChatMessage;
import java.util.List;

public record AgentRunRequest(RuntimeContextScope context, List<ChatMessage> messages) {
}
