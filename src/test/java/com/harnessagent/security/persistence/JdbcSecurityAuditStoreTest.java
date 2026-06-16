package com.harnessagent.security.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import com.harnessagent.security.domain.ResourceType;
import com.harnessagent.security.persistence.JdbcSecurityAuditStore;
import com.harnessagent.security.persistence.SecurityAuditRecord;

class JdbcSecurityAuditStoreTest {

    @Test
    void sharesAuditRecordsAcrossInstancesAndFiltersByTenantAndRetentionCutoff() {
        DataSource dataSource = database();
        JdbcSecurityAuditStore writer = store(dataSource);
        JdbcSecurityAuditStore reader = store(dataSource);

        writer.save(record("old", "tenant-a", Instant.parse("2026-06-01T00:00:00Z")));
        writer.save(record("kept", "tenant-a", Instant.parse("2026-06-15T08:00:00Z")));
        writer.save(record("other", "tenant-b", Instant.parse("2026-06-15T08:00:00Z")));

        assertThat(reader.search("tenant-a", Instant.parse("2026-06-10T00:00:00Z")))
                .extracting(SecurityAuditRecord::id)
                .containsExactly("kept");
        assertThat(reader.search("tenant-b", Instant.EPOCH))
                .extracting(SecurityAuditRecord::id)
                .containsExactly("other");
    }

    private static SecurityAuditRecord record(String id, String tenantId, Instant occurredAt) {
        return new SecurityAuditRecord(
                id,
                occurredAt,
                tenantId,
                "user-a",
                ResourceType.TOOL,
                "tool-a",
                "CONFIRMED",
                Map.of("token", "[REDACTED]", "count", 1));
    }

    private static JdbcSecurityAuditStore store(DataSource dataSource) {
        return new JdbcSecurityAuditStore(new NamedParameterJdbcTemplate(dataSource));
    }

    private static DataSource database() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("classpath:db/migration/V1__durable_persistence.sql")
                .build();
    }
}
