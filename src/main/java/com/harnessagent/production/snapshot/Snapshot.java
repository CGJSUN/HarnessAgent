package com.harnessagent.production.snapshot;

import java.util.Arrays;

public record Snapshot(
        SnapshotMetadata metadata,
        byte[] content) {

    public Snapshot {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
