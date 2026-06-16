package com.harnessagent.production.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harnessagent.runtime.RuntimeContextScope;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.harnessagent.production.infrastructure.InMemorySnapshotStore;
import com.harnessagent.production.snapshot.SnapshotMetadata;
import com.harnessagent.production.snapshot.SnapshotStorePlan;
import com.harnessagent.production.snapshot.SnapshotStoreType;
import com.harnessagent.production.workspace.WorkspaceMode;
import com.harnessagent.production.workspace.WorkspacePlan;
import com.harnessagent.production.workspace.WorkspaceSnapshotService;

class WorkspaceSnapshotServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndRestoresWorkspaceSnapshotWithAuthorization() throws Exception {
        InMemorySnapshotStore store = new InMemorySnapshotStore(SnapshotStoreType.JDBC, "memory://snapshots");
        WorkspaceSnapshotService service = new WorkspaceSnapshotService(store);
        RuntimeContextScope context = context("tenant-a", "agent-a", "session-a");
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace.resolve("nested"));
        Files.writeString(workspace.resolve("nested/state.txt"), "workspace-state");
        WorkspacePlan plan = new WorkspacePlan(
                WorkspaceMode.SANDBOX,
                workspace.toString(),
                "sandbox:latest",
                new SnapshotStorePlan(SnapshotStoreType.JDBC, "memory://snapshots"));

        SnapshotMetadata metadata = service.save(context, plan, workspace, "task-a").orElseThrow();
        Path restored = tempDir.resolve("restored");

        service.restore(context, metadata.id(), restored);

        assertThat(Files.readString(restored.resolve("nested/state.txt"))).isEqualTo("workspace-state");
    }

    @Test
    void rejectsSnapshotRestoreForDifferentTenantAgentOrSession() throws Exception {
        InMemorySnapshotStore store = new InMemorySnapshotStore(SnapshotStoreType.JDBC, "memory://snapshots");
        WorkspaceSnapshotService service = new WorkspaceSnapshotService(store);
        RuntimeContextScope owner = context("tenant-a", "agent-a", "session-a");
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("state.txt"), "workspace-state");
        WorkspacePlan plan = new WorkspacePlan(
                WorkspaceMode.SANDBOX,
                workspace.toString(),
                "sandbox:latest",
                new SnapshotStorePlan(SnapshotStoreType.JDBC, "memory://snapshots"));
        SnapshotMetadata metadata = service.save(owner, plan, workspace, "task-a").orElseThrow();

        assertThatThrownBy(() -> service.restore(
                context("tenant-b", "agent-a", "session-a"),
                metadata.id(),
                tempDir.resolve("forbidden")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not belong");
    }

    private static RuntimeContextScope context(String tenantId, String agentId, String sessionId) {
        return new RuntimeContextScope(
                tenantId,
                "user-a",
                agentId,
                sessionId,
                "runtime-user",
                "runtime-session");
    }
}
