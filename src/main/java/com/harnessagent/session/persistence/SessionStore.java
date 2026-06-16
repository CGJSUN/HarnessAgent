package com.harnessagent.session.persistence;

import com.harnessagent.runtime.RuntimeContextScope;
import java.util.List;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.domain.SessionSummary;

public interface SessionStore {

    void appendMessage(RuntimeContextScope context, ChatMessage message);

    List<SessionSummary> listSessions(String tenantId, String userId, String agentId);

    List<ChatMessage> listMessages(RuntimeContextScope context);

    boolean deleteSession(RuntimeContextScope context);
}
