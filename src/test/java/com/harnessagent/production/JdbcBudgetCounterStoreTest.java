package com.harnessagent.production;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcBudgetCounterStoreTest {

    @Test
    void sharesCountersAcrossInstances() {
        DataSource dataSource = database();
        JdbcBudgetCounterStore first = store(dataSource);
        JdbcBudgetCounterStore second = store(dataSource);

        assertThat(first.increment("tenant:tenant-a", 10))
                .isEqualTo(new BudgetCounter("tenant:tenant-a", 1, 10));
        assertThat(second.increment("tenant:tenant-a", 5))
                .isEqualTo(new BudgetCounter("tenant:tenant-a", 2, 15));
        assertThat(second.increment("tenant:tenant-b", 7))
                .isEqualTo(new BudgetCounter("tenant:tenant-b", 1, 7));
    }

    private static JdbcBudgetCounterStore store(DataSource dataSource) {
        return new JdbcBudgetCounterStore(new NamedParameterJdbcTemplate(dataSource));
    }

    private static DataSource database() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("classpath:db/migration/V1__durable_persistence.sql")
                .build();
    }
}
