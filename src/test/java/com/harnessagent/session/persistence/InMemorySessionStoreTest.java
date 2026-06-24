package com.harnessagent.session.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.chat.domain.ContentBlock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.domain.SessionSummary;
import com.harnessagent.session.persistence.InMemorySessionStore;

class InMemorySessionStoreTest {

    private final RuntimeContextFactory contextFactory = new RuntimeContextFactory();
    private final InMemorySessionStore store = new InMemorySessionStore();

    @Test
    void keepsMessagesIsolatedByOwnerAgentAndSession() {
        RuntimeContextScope base = contextFactory.create("owner-scope-a", "user-a", "agent-a", "session-a");
        RuntimeContextScope otherOwnerScope = contextFactory.create("owner-scope-b", "user-a", "agent-a", "session-a");
        RuntimeContextScope otherSession = contextFactory.create("owner-scope-a", "user-a", "agent-a", "session-b");

        store.appendMessage(base, ChatMessage.user("hello"));
        store.appendMessage(otherOwnerScope, ChatMessage.user("owner-scope-b"));
        store.appendMessage(otherSession, ChatMessage.user("session-b"));

        assertThat(store.listMessages(base)).extracting(ChatMessage::content).containsExactly("hello");
        assertThat(store.listSessions("owner-scope-a", "user-a", "agent-a"))
                .extracting(SessionSummary::sessionId)
                .containsExactlyInAnyOrder("session-a", "session-b");
    }

    @Test
    void deletesOnlyTargetSession() {
        RuntimeContextScope first = contextFactory.create("owner-scope-a", "user-a", "agent-a", "session-a");
        RuntimeContextScope second = contextFactory.create("owner-scope-a", "user-a", "agent-a", "session-b");
        store.appendMessage(first, ChatMessage.user("first"));
        store.appendMessage(second, ChatMessage.user("second"));

        assertThat(store.deleteSession(first)).isTrue();

        assertThat(store.listMessages(first)).isEmpty();
        assertThat(store.listMessages(second)).extracting(ChatMessage::content).containsExactly("second");
    }

    @Test
    void preservesStructuredContentBlocks() {
        RuntimeContextScope context = contextFactory.create("owner-scope-a", "user-a", "agent-a", "session-blocks");
        ChatMessage message = ChatMessage.assistant(List.of(
                ContentBlock.text("summary"),
                ContentBlock.file("workspace://files/report.pdf", "application/pdf", "report.pdf"),
                ContentBlock.thinking("checked notes"),
                ContentBlock.toolResult("search.docs", Map.of("matches", 2))));

        store.appendMessage(context, message);

        assertThat(store.listMessages(context)).singleElement()
                .satisfies(stored -> {
                    assertThat(stored.content()).isEqualTo("summary");
                    assertThat(stored.contentBlocks()).hasSize(4);
                    assertThat(stored.contentBlocks().get(1).uri()).isEqualTo("workspace://files/report.pdf");
                    assertThat(stored.contentBlocks().get(2).text()).isEqualTo("checked notes");
                    assertThat(stored.contentBlocks().get(3).metadata()).containsEntry("toolName", "search.docs");
                });
    }
}
