package com.harnessagent.session.persistence;

import com.harnessagent.runtime.RuntimeContextScope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.domain.SessionSummary;

@Repository
@Profile("!production")
public class InMemorySessionStore implements SessionStore {

    private final Map<SessionKey, List<ChatMessage>> sessions = new ConcurrentHashMap<>();

    @Override
    public void appendMessage(RuntimeContextScope context, ChatMessage message) {
        SessionKey key = SessionKey.from(context);
        sessions.compute(key, (ignored, existing) -> {
            List<ChatMessage> messages = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            messages.add(message);
            return messages;
        });
    }

    @Override
    public List<SessionSummary> listSessions(String tenantId, String userId, String agentId) {
        return sessions.entrySet().stream()
                .filter(entry -> entry.getKey().matches(tenantId, userId, agentId))
                .map(entry -> toSummary(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(SessionSummary::lastMessageAt).reversed())
                .toList();
    }

    @Override
    public List<ChatMessage> listMessages(RuntimeContextScope context) {
        return List.copyOf(sessions.getOrDefault(SessionKey.from(context), List.of()));
    }

    @Override
    public boolean deleteSession(RuntimeContextScope context) {
        return sessions.remove(SessionKey.from(context)) != null;
    }

    private static SessionSummary toSummary(SessionKey key, List<ChatMessage> messages) {
        Instant lastMessageAt = messages.stream()
                .map(ChatMessage::createdAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
        return new SessionSummary(
                key.tenantId(), key.userId(), key.agentId(), key.sessionId(), messages.size(), lastMessageAt);
    }

    private record SessionKey(String tenantId, String userId, String agentId, String sessionId) {
        static SessionKey from(RuntimeContextScope context) {
            return new SessionKey(
                    context.tenantId(), context.userId(), context.agentId(), context.sessionId());
        }

        boolean matches(String tenantId, String userId, String agentId) {
            return this.tenantId.equals(tenantId)
                    && this.userId.equals(userId)
                    && (agentId == null || agentId.isBlank() || this.agentId.equals(agentId));
        }
    }
}
