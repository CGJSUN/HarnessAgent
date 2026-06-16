package com.harnessagent.session.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.runtime.RuntimeContextScope;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.domain.MessageRole;
import com.harnessagent.session.persistence.JdbcSessionStore;

class JdbcSessionStoreTest {

    @Test
    void sharesMessagesAcrossInstancesAndDeletesOnlyTargetTenantSession() {
        DataSource dataSource = database();
        JdbcSessionStore writer = store(dataSource);
        JdbcSessionStore reader = store(dataSource);
        RuntimeContextScope base = context("tenant-a", "user-a", "agent-a", "session-a");
        RuntimeContextScope otherTenant = context("tenant-b", "user-a", "agent-a", "session-a");

        writer.appendMessage(base, new ChatMessage("m1", MessageRole.USER, "hello", Instant.parse("2026-06-15T08:00:00Z")));
        writer.appendMessage(base, new ChatMessage("m2", MessageRole.ASSISTANT, "hi", Instant.parse("2026-06-15T08:00:01Z")));
        writer.appendMessage(otherTenant, new ChatMessage("m3", MessageRole.USER, "isolated", Instant.parse("2026-06-15T08:00:02Z")));

        assertThat(reader.listMessages(base))
                .extracting(ChatMessage::content)
                .containsExactly("hello", "hi");
        assertThat(reader.listSessions("tenant-a", "user-a", "agent-a"))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.sessionId()).isEqualTo("session-a");
                    assertThat(summary.messageCount()).isEqualTo(2);
                    assertThat(summary.lastMessageAt()).isEqualTo(Instant.parse("2026-06-15T08:00:01Z"));
                });

        assertThat(reader.deleteSession(base)).isTrue();

        assertThat(writer.listMessages(base)).isEmpty();
        assertThat(writer.listMessages(otherTenant))
                .extracting(ChatMessage::content)
                .containsExactly("isolated");
    }

    private static JdbcSessionStore store(DataSource dataSource) {
        return new JdbcSessionStore(new NamedParameterJdbcTemplate(dataSource));
    }

    private static DataSource database() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("classpath:db/migration/V1__durable_persistence.sql")
                .build();
    }

    private static RuntimeContextScope context(String tenantId, String userId, String agentId, String sessionId) {
        return new RuntimeContextScope(tenantId, userId, agentId, sessionId, userId, sessionId);
    }
}
