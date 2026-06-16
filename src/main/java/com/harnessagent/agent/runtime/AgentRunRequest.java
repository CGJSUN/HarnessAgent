package com.harnessagent.agent.runtime;

import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.session.domain.ChatMessage;
import java.util.List;

public record AgentRunRequest(RuntimeContextScope context, List<ChatMessage> messages) {
}
