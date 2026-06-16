package com.harnessagent.production.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import com.harnessagent.production.infrastructure.InMemorySnapshotStore;
import com.harnessagent.production.snapshot.SnapshotMetadata;
import com.harnessagent.production.snapshot.SnapshotStore;
import com.harnessagent.production.snapshot.SnapshotStoreType;

class SnapshotStoreTest {

    @Test
    void savesLoadsListsAndDeletesSnapshotsWithBackendMetadata() {
        SnapshotStore store = new InMemorySnapshotStore("memory://snapshots");
        SnapshotMetadata requested = new SnapshotMetadata(
                null,
                "tenant-a",
                "agent-a",
                "session-a",
                "task-a",
                null,
                SnapshotStoreType.JDBC,
                "jdbc://snapshot/ignored");

        SnapshotMetadata saved = store.save(requested, "workspace-state".getBytes(StandardCharsets.UTF_8));

        assertThat(saved.id()).isNotBlank();
        assertThat(saved.backendType()).isEqualTo(SnapshotStoreType.NONE);
        assertThat(saved.location()).isEqualTo("memory://snapshots/" + saved.id());
        assertThat(store.list("tenant-a", "agent-a", "session-a")).containsExactly(saved);
        assertThat(store.load(saved.id()))
                .get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.metadata()).isEqualTo(saved);
                    assertThat(new String(snapshot.content(), StandardCharsets.UTF_8)).isEqualTo("workspace-state");
                });
        assertThat(store.delete(saved.id())).isTrue();
        assertThat(store.load(saved.id())).isEmpty();
    }
}
