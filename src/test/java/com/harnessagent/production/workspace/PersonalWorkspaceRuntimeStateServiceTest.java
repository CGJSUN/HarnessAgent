package com.harnessagent.production.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.production.infrastructure.InMemorySnapshotStore;
import com.harnessagent.production.snapshot.SnapshotMetadata;
import com.harnessagent.production.snapshot.SnapshotStorePlan;
import com.harnessagent.production.snapshot.SnapshotStoreType;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.workspace.application.PersonalWorkspaceRuntimeStateService;
import com.harnessagent.workspace.application.PersonalWorkspaceService;
import com.harnessagent.workspace.domain.PersonalWorkspaceLayout;
import com.harnessagent.workspace.domain.PersonalWorkspaceRuntimeState;
import com.harnessagent.workspace.domain.WorkspaceSnapshotReferenceType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersonalWorkspaceRuntimeStateServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void recordsWorkspaceAndSandboxSnapshotReferencesAndRestoresLongTaskState() throws Exception {
        RuntimeContextScope context = new RuntimeContextFactory()
                .create("personal", "owner-a", "personal-agent", "session-a");
        PersonalWorkspaceService workspaceService = new PersonalWorkspaceService(properties());
        PersonalWorkspaceRuntimeStateService stateService = new PersonalWorkspaceRuntimeStateService(workspaceService);
        InMemorySnapshotStore store = new InMemorySnapshotStore(SnapshotStoreType.JDBC, "memory://snapshots");
        WorkspaceSnapshotService snapshotService = new WorkspaceSnapshotService(store);
        WorkspacePlan plan = new WorkspacePlan(
                WorkspaceMode.SANDBOX,
                "/workspace",
                "sandbox:latest",
                new SnapshotStorePlan(SnapshotStoreType.JDBC, "memory://snapshots"));
        PersonalWorkspaceLayout layout = workspaceService.initialize(context);
        Files.writeString(layout.artifactsDirectory().resolve("result.txt"), "workspace-state");
        Path sandbox = tempDir.resolve("sandbox");
        Files.createDirectories(sandbox.resolve("state"));
        Files.writeString(sandbox.resolve("state/stdout.txt"), "sandbox-state");

        SnapshotMetadata workspaceSnapshot = snapshotService.save(context, plan, layout.root(), "task-a").orElseThrow();
        SnapshotMetadata sandboxSnapshot = snapshotService.save(context, plan, sandbox, "task-a").orElseThrow();
        stateService.saveReference(
                context,
                WorkspaceSnapshotReferenceType.WORKSPACE,
                workspaceSnapshot,
                "task-a",
                "workspace://plans/task-a.md");
        stateService.saveReference(
                context,
                WorkspaceSnapshotReferenceType.SANDBOX,
                sandboxSnapshot,
                "task-a",
                "workspace://plans/task-a.md");

        PersonalWorkspaceRuntimeState reloaded = new PersonalWorkspaceRuntimeStateService(workspaceService)
                .load(context)
                .orElseThrow();
        Files.delete(layout.artifactsDirectory().resolve("result.txt"));

        assertThat(reloaded.workspaceSnapshot().snapshotId()).isEqualTo(workspaceSnapshot.id());
        assertThat(reloaded.sandboxSnapshot().snapshotId()).isEqualTo(sandboxSnapshot.id());
        assertThat(reloaded.planPath()).isEqualTo("workspace://plans/task-a.md");
        assertThat(stateService.restoreWorkspaceSnapshot(context, snapshotService)).isPresent();
        assertThat(Files.readString(layout.artifactsDirectory().resolve("result.txt"))).isEqualTo("workspace-state");
        assertThat(stateService.restoreSandboxSnapshot(context, snapshotService)).isPresent();
        try (var paths = Files.walk(layout.sessionsDirectory())) {
            assertThat(paths
                    .filter(Files::isRegularFile)
                    .anyMatch(path -> path.getFileName().toString().equals("stdout.txt")))
                    .isTrue();
        }
    }

    private HarnessAgentProperties properties() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setWorkspace(tempDir.resolve("workspaces/personal-agent").toString());
        properties.getAgents().put("personal-agent", agent);
        return properties;
    }
}
