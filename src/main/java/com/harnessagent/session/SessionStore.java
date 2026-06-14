package com.harnessagent.session;

import com.harnessagent.runtime.RuntimeContextScope;
import java.util.List;

public interface SessionStore {

    void appendMessage(RuntimeContextScope context, ChatMessage message);

    List<SessionSummary> listSessions(String tenantId, String userId, String agentId);

    List<ChatMessage> listMessages(RuntimeContextScope context);

    boolean deleteSession(RuntimeContextScope context);
}
