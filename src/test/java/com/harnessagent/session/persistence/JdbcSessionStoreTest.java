package com.harnessagent.session.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.chat.domain.ContentBlock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    @Test
    void roundTripsStructuredContentBlocks() {
        DataSource dataSource = database();
        JdbcSessionStore writer = store(dataSource);
        JdbcSessionStore reader = store(dataSource);
        RuntimeContextScope context = context("tenant-a", "user-a", "agent-a", "session-blocks");
        ChatMessage message = new ChatMessage(
                "m-blocks",
                MessageRole.ASSISTANT,
                List.of(
                        ContentBlock.text("summary"),
                        ContentBlock.file("workspace://files/report.pdf", "application/pdf", "report.pdf"),
                        ContentBlock.image("workspace://files/chart.png", "image/png", "chart.png"),
                        ContentBlock.audio("workspace://files/audio.mp3", "audio/mpeg", "audio.mp3"),
                        ContentBlock.video("workspace://files/demo.mp4", "video/mp4", "demo.mp4"),
                        ContentBlock.thinking("checked evidence"),
                        ContentBlock.toolResult("search.docs", Map.of("matches", 2))),
                Instant.parse("2026-06-15T08:00:03Z"));

        writer.appendMessage(context, message);

        assertThat(reader.listMessages(context)).singleElement()
                .satisfies(stored -> {
                    assertThat(stored.content()).isEqualTo("summary");
                    assertThat(stored.contentBlocks()).hasSize(7);
                    assertThat(stored.contentBlocks().get(1).uri()).isEqualTo("workspace://files/report.pdf");
                    assertThat(stored.contentBlocks().get(2).mimeType()).isEqualTo("image/png");
                    assertThat(stored.contentBlocks().get(5).text()).isEqualTo("checked evidence");
                    assertThat(stored.contentBlocks().get(6).metadata()).containsEntry("toolName", "search.docs");
                });
    }

    @Test
    void fallsBackToLegacyContentWhenContentBlocksAreEmpty() {
        DataSource dataSource = database();
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcSessionStore reader = new JdbcSessionStore(jdbc);
        RuntimeContextScope context = context("tenant-a", "user-a", "agent-a", "session-legacy");
        jdbc.update("""
                insert into ha_session_messages (
                    id, tenant_id, user_id, agent_id, session_id, role, content, content_blocks_json, created_at
                ) values (
                    'legacy-1', 'tenant-a', 'user-a', 'agent-a', 'session-legacy', 'ASSISTANT', 'legacy text', '[]', '2026-06-15 08:00:00'
                )
                """, Map.of());

        assertThat(reader.listMessages(context)).singleElement()
                .satisfies(stored -> {
                    assertThat(stored.content()).isEqualTo("legacy text");
                    assertThat(stored.contentBlocks()).singleElement()
                            .satisfies(block -> assertThat(block.text()).isEqualTo("legacy text"));
                });
    }

    private static JdbcSessionStore store(DataSource dataSource) {
        return new JdbcSessionStore(new NamedParameterJdbcTemplate(dataSource));
    }

    private static DataSource database() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("classpath:db/migration/V1__durable_persistence.sql")
                .addScript("classpath:db/migration/V3__session_message_content_blocks.sql")
                .build();
    }

    private static RuntimeContextScope context(String tenantId, String userId, String agentId, String sessionId) {
        return new RuntimeContextScope(tenantId, userId, agentId, sessionId, userId, sessionId);
    }
}
