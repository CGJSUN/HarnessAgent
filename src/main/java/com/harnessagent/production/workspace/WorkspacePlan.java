package com.harnessagent.production.workspace;

import com.harnessagent.production.snapshot.SnapshotStorePlan;

public record WorkspacePlan(
        WorkspaceMode mode,
        String location,
        String sandboxImage,
        SnapshotStorePlan snapshotStore) {
}
