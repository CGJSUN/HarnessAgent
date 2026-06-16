package com.harnessagent.production.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import com.harnessagent.production.infrastructure.JdbcSnapshotStore;
import com.harnessagent.production.snapshot.SnapshotMetadata;
import com.harnessagent.production.snapshot.SnapshotStoreType;

class JdbcSnapshotStoreTest {

    @Test
    void sharesSnapshotsAcrossInstancesAndDeletesById() {
        DataSource dataSource = database();
        JdbcSnapshotStore writer = store(dataSource);
        JdbcSnapshotStore reader = store(dataSource);
        SnapshotMetadata metadata = new SnapshotMetadata(
                "snapshot-a",
                "tenant-a",
                "agent-a",
                "session-a",
                "task-a",
                Instant.parse("2026-06-15T08:00:00Z"),
                SnapshotStoreType.S3,
                "ignored");

        SnapshotMetadata saved = writer.save(metadata, "workspace".getBytes(StandardCharsets.UTF_8));

        assertThat(saved.backendType()).isEqualTo(SnapshotStoreType.JDBC);
        assertThat(saved.location()).isEqualTo("jdbc://snapshot/snapshot-a");
        assertThat(reader.list("tenant-a", "agent-a", "session-a")).containsExactly(saved);
        assertThat(reader.list("tenant-b", "agent-a", "session-a")).isEmpty();
        assertThat(reader.load("snapshot-a"))
                .get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.metadata()).isEqualTo(saved);
                    assertThat(new String(snapshot.content(), StandardCharsets.UTF_8)).isEqualTo("workspace");
                });

        assertThat(reader.delete("snapshot-a")).isTrue();

        assertThat(writer.load("snapshot-a")).isEmpty();
    }

    private static JdbcSnapshotStore store(DataSource dataSource) {
        return new JdbcSnapshotStore(new NamedParameterJdbcTemplate(dataSource));
    }

    private static DataSource database() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("classpath:db/migration/V1__durable_persistence.sql")
                .build();
    }
}
