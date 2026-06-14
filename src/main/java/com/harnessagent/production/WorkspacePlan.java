package com.harnessagent.production;

public record WorkspacePlan(
        WorkspaceMode mode,
        String location,
        String sandboxImage,
        SnapshotStorePlan snapshotStore) {
}
