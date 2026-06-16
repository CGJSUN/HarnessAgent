package com.harnessagent.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.chat.domain.AgentSessionRecoverySnapshot;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.health.ProductionRuntimeValidator;
import com.harnessagent.production.infrastructure.JdbcAgentStateStore;
import com.harnessagent.production.infrastructure.LocalJsonAgentStateStore;
import com.harnessagent.production.state.StateStorePlan;
import com.harnessagent.production.state.TenantStateKeyStrategy;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.persistence.JdbcSessionStore;
import java.nio.file.Path;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class AgentSessionRecoveryServiceTest {

    private final RuntimeContextFactory contextFactory = new RuntimeContextFactory();

    @TempDir
    Path tempDir;

    @Test
    void restoresMessagesAgentStateAndPendingExecutionAcrossJdbcInstances() {
        DataSource dataSource = database();
        RuntimeContextScope context = contextFactory.create("personal", "owner-a", "agent-a", "session-a");
        NamedParameterJdbcTemplate firstJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcSessionStore writerSessionStore = new JdbcSessionStore(firstJdbc);
        JdbcAgentStateStore writerStateStore = new JdbcAgentStateStore(
                firstJdbc,
                new TenantStateKeyStrategy(),
                StateStorePlan.mysql("jdbc:h2:mem"));
        writerSessionStore.appendMessage(context, ChatMessage.user("hello"));
        writerSessionStore.appendMessage(context, ChatMessage.assistant("hi"));
        writerStateStore.save(context, agentScope(context, "agent_state"), "{\"step\":1}");
        AgentSessionRecoveryService writer = new AgentSessionRecoveryService(
                writerSessionStore,
                mysqlValidator(),
                plan -> writerStateStore);
        writer.markPending(context, "complete", "message-1");

        NamedParameterJdbcTemplate secondJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAgentStateStore readerStateStore = new JdbcAgentStateStore(
                secondJdbc,
                new TenantStateKeyStrategy(),
                StateStorePlan.mysql("jdbc:h2:mem"));
        AgentSessionRecoveryService reader = new AgentSessionRecoveryService(
                new JdbcSessionStore(secondJdbc),
                mysqlValidator(),
                plan -> readerStateStore);

        AgentSessionRecoverySnapshot snapshot = reader.snapshot(context);

        assertThat(snapshot.messages()).extracting(ChatMessage::content).containsExactly("hello", "hi");
        assertThat(snapshot.agentStatePresent()).isTrue();
        assertThat(reader.agentScopeStatePresent(context)).isTrue();
        assertThat(snapshot.agentStateScopes()).contains(agentScope(context, "agent_state"));
        assertThat(snapshot.pendingExecution()).get()
                .satisfies(pending -> {
                    assertThat(pending.mode()).isEqualTo("complete");
                    assertThat(pending.status()).isEqualTo("PENDING");
                    assertThat(pending.runtimeUserId()).isEqualTo("personal:owner-a");
                    assertThat(pending.runtimeSessionId()).isEqualTo("agent-a:session-a");
                    assertThat(pending.startedAt()).isBeforeOrEqualTo(Instant.now());
                });

        reader.clearPending(context);

        assertThat(writer.pendingExecution(context)).isEmpty();
    }

    @Test
    void pendingExecutionDoesNotCountAsAgentScopeState() {
        DataSource dataSource = database();
        RuntimeContextScope context = contextFactory.create("personal", "owner-a", "agent-a", "session-pending");
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAgentStateStore stateStore = new JdbcAgentStateStore(
                jdbc,
                new TenantStateKeyStrategy(),
                StateStorePlan.mysql("jdbc:h2:mem"));
        AgentSessionRecoveryService service = new AgentSessionRecoveryService(
                new JdbcSessionStore(jdbc),
                mysqlValidator(),
                plan -> stateStore);

        service.markPending(context, "stream", "message-1");

        AgentSessionRecoverySnapshot snapshot = service.snapshot(context);
        assertThat(snapshot.pendingExecution()).isPresent();
        assertThat(snapshot.agentStateScopes()).contains(AgentSessionRecoveryService.PENDING_EXECUTION_SCOPE);
        assertThat(snapshot.agentStatePresent()).isFalse();
        assertThat(service.agentScopeStatePresent(context)).isFalse();
    }

    @Test
    void localJsonStateStoreRestoresAgentStateAcrossInstances() {
        StateStorePlan plan = StateStorePlan.local(tempDir.resolve("agent-state").toString());
        TenantStateKeyStrategy keyStrategy = new TenantStateKeyStrategy();
        RuntimeContextScope context = contextFactory.create("personal", "owner-a", "agent-a", "session-a");

        new LocalJsonAgentStateStore(keyStrategy, plan)
                .save(context, agentScope(context, "agent_state"), "{\"remembered\":true}");

        LocalJsonAgentStateStore restored = new LocalJsonAgentStateStore(keyStrategy, plan);

        assertThat(restored.load(context, agentScope(context, "agent_state")))
                .get()
                .satisfies(entry -> assertThat(entry.value()).isEqualTo("{\"remembered\":true}"));
        assertThat(restored.listSessionScopes(context)).containsExactly(agentScope(context, "agent_state"));
    }

    private static ProductionRuntimeValidator mysqlValidator() {
        ProductionRuntimeProperties properties = new ProductionRuntimeProperties();
        properties.getStateStore().setType(com.harnessagent.production.state.StateStoreType.MYSQL);
        return new ProductionRuntimeValidator(properties);
    }

    private static String agentScope(RuntimeContextScope context, String key) {
        return "agentscope:" + context.runtimeUserId() + ":" + context.runtimeSessionId() + ":" + key;
    }

    private static DataSource database() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("classpath:db/migration/V1__durable_persistence.sql")
                .addScript("classpath:db/migration/V3__session_message_content_blocks.sql")
                .build();
    }
}
