package com.harnessagent.production.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import com.harnessagent.production.budget.BudgetCounter;
import com.harnessagent.production.infrastructure.JdbcBudgetCounterStore;

class JdbcBudgetCounterStoreTest {

    @Test
    void sharesCountersAcrossInstances() {
        DataSource dataSource = database();
        JdbcBudgetCounterStore first = store(dataSource);
        JdbcBudgetCounterStore second = store(dataSource);

        assertThat(first.increment("owner-scope:owner-scope-a", 10))
                .isEqualTo(new BudgetCounter("owner-scope:owner-scope-a", 1, 10));
        assertThat(second.increment("owner-scope:owner-scope-a", 5))
                .isEqualTo(new BudgetCounter("owner-scope:owner-scope-a", 2, 15));
        assertThat(second.increment("owner-scope:owner-scope-b", 7))
                .isEqualTo(new BudgetCounter("owner-scope:owner-scope-b", 1, 7));
    }

    private static JdbcBudgetCounterStore store(DataSource dataSource) {
        return new JdbcBudgetCounterStore(new NamedParameterJdbcTemplate(dataSource));
    }

    private static DataSource database() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("classpath:db/migration/V1__durable_persistence.sql")
                .addScript("classpath:db/migration/V3__session_message_content_blocks.sql")
                .addScript("classpath:db/migration/V5__tool_workload_type.sql")
                .addScript("classpath:db/migration/V7__personal_memory_rag_metadata.sql")
                .addScript("classpath:db/migration/V9__personal_tooling_hitl.sql")
                .addScript("classpath:db/migration/V11__owner_scope_persistence.sql")
                .build();
    }
}
