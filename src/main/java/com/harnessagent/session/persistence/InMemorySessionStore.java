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
    public List<SessionSummary> listSessions(String ownerScopeId, String ownerId, String agentId) {
        return sessions.entrySet().stream()
                .filter(entry -> entry.getKey().matches(ownerScopeId, ownerId, agentId))
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
                key.ownerScopeId(), key.ownerId(), key.agentId(), key.sessionId(), messages.size(), lastMessageAt);
    }

    private record SessionKey(String ownerScopeId, String ownerId, String agentId, String sessionId) {
        static SessionKey from(RuntimeContextScope context) {
            return new SessionKey(
                    context.ownerScopeId(), context.ownerId(), context.agentId(), context.sessionId());
        }

        boolean matches(String ownerScopeId, String ownerId, String agentId) {
            return this.ownerScopeId.equals(ownerScopeId)
                    && this.ownerId.equals(ownerId)
                    && (agentId == null || agentId.isBlank() || this.agentId.equals(agentId));
        }
    }
}
