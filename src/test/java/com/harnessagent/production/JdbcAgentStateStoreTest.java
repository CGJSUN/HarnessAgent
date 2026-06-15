package com.harnessagent.production;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.runtime.RuntimeContextScope;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcAgentStateStoreTest {

    @Test
    void sharesStateAcrossInstancesAndDeletesSessionScopesWithTenantIsolation() {
        DataSource dataSource = database();
        TenantStateKeyStrategy keyStrategy = new TenantStateKeyStrategy();
        JdbcAgentStateStore first = store(dataSource, keyStrategy);
        JdbcAgentStateStore second = store(dataSource, keyStrategy);
        RuntimeContextScope base = context("tenant-a", "user-a", "agent-a", "session-a");
        RuntimeContextScope otherTenant = context("tenant-b", "user-a", "agent-a", "session-a");

        first.save(base, "agentscope:runtime-user:runtime-session:memory", "{\"step\":1}");
        first.save(otherTenant, "agentscope:runtime-user:runtime-session:memory", "{\"step\":2}");

        assertThat(second.load(base, "agentscope:runtime-user:runtime-session:memory"))
                .get()
                .extracting(AgentStateEntry::value)
                .isEqualTo("{\"step\":1}");
        assertThat(second.listSessionScopes(base))
                .containsExactly("agentscope:runtime-user:runtime-session:memory");

        assertThat(second.deleteSession(base, "agentscope:runtime-user:runtime-session")).isTrue();

        assertThat(first.load(base, "agentscope:runtime-user:runtime-session:memory")).isEmpty();
        assertThat(first.load(otherTenant, "agentscope:runtime-user:runtime-session:memory")).isPresent();
    }

    private static JdbcAgentStateStore store(DataSource dataSource, TenantStateKeyStrategy keyStrategy) {
        return new JdbcAgentStateStore(
                new NamedParameterJdbcTemplate(dataSource),
                keyStrategy,
                StateStorePlan.mysql("jdbc:h2:mem"));
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
