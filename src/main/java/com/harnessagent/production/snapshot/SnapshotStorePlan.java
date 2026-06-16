package com.harnessagent.production.snapshot;

public record SnapshotStorePlan(
        SnapshotStoreType type,
        String uri) {

    public boolean enabled() {
        return type != SnapshotStoreType.NONE;
    }
}
