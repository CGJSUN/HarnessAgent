package com.harnessagent.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import org.junit.jupiter.api.Test;

class InMemorySessionStoreTest {

    private final RuntimeContextFactory contextFactory = new RuntimeContextFactory();
    private final InMemorySessionStore store = new InMemorySessionStore();

    @Test
    void keepsMessagesIsolatedByTenantUserAgentAndSession() {
        RuntimeContextScope base = contextFactory.create("tenant-a", "user-a", "agent-a", "session-a");
        RuntimeContextScope otherTenant = contextFactory.create("tenant-b", "user-a", "agent-a", "session-a");
        RuntimeContextScope otherSession = contextFactory.create("tenant-a", "user-a", "agent-a", "session-b");

        store.appendMessage(base, ChatMessage.user("hello"));
        store.appendMessage(otherTenant, ChatMessage.user("tenant-b"));
        store.appendMessage(otherSession, ChatMessage.user("session-b"));

        assertThat(store.listMessages(base)).extracting(ChatMessage::content).containsExactly("hello");
        assertThat(store.listSessions("tenant-a", "user-a", "agent-a"))
                .extracting(SessionSummary::sessionId)
                .containsExactlyInAnyOrder("session-a", "session-b");
    }

    @Test
    void deletesOnlyTargetSession() {
        RuntimeContextScope first = contextFactory.create("tenant-a", "user-a", "agent-a", "session-a");
        RuntimeContextScope second = contextFactory.create("tenant-a", "user-a", "agent-a", "session-b");
        store.appendMessage(first, ChatMessage.user("first"));
        store.appendMessage(second, ChatMessage.user("second"));

        assertThat(store.deleteSession(first)).isTrue();

        assertThat(store.listMessages(first)).isEmpty();
        assertThat(store.listMessages(second)).extracting(ChatMessage::content).containsExactly("second");
    }
}
