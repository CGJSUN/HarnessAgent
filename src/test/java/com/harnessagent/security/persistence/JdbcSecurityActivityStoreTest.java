package com.harnessagent.security.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import com.harnessagent.persistence.JdbcStoreTestSupport;
import com.harnessagent.security.domain.ResourceType;
import com.harnessagent.security.persistence.JdbcSecurityActivityStore;
import com.harnessagent.security.persistence.SecurityActivityRecord;

class JdbcSecurityActivityStoreTest {

    @Test
    void sharesActivityRecordsAcrossInstancesAndFiltersByOwnerScopeAndRetentionCutoff() {
        DataSource dataSource = database();
        JdbcSecurityActivityStore writer = store(dataSource);
        JdbcSecurityActivityStore reader = store(dataSource);

        writer.save(record("old", "owner-scope-a", Instant.parse("2026-06-01T00:00:00Z")));
        writer.save(record("kept", "owner-scope-a", Instant.parse("2026-06-15T08:00:00Z")));
        writer.save(record("other", "owner-scope-b", Instant.parse("2026-06-15T08:00:00Z")));

        assertThat(reader.search("owner-scope-a", Instant.parse("2026-06-10T00:00:00Z")))
                .extracting(SecurityActivityRecord::id)
                .containsExactly("kept");
        assertThat(reader.search("owner-scope-b", Instant.EPOCH))
                .extracting(SecurityActivityRecord::id)
                .containsExactly("other");
    }

    private static SecurityActivityRecord record(String id, String ownerScopeId, Instant occurredAt) {
        return new SecurityActivityRecord(
                id,
                occurredAt,
                ownerScopeId,
                "user-a",
                ResourceType.TOOL,
                "tool-a",
                "CONFIRMED",
                Map.of("token", "[REDACTED]", "count", 1));
    }

    private static JdbcSecurityActivityStore store(DataSource dataSource) {
        return new JdbcSecurityActivityStore(new NamedParameterJdbcTemplate(dataSource));
    }

    private static DataSource database() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        JdbcStoreTestSupport.createSecurityActivityTables(new JdbcTemplate(dataSource));
        return dataSource;
    }
}
