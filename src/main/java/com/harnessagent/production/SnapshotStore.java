package com.harnessagent.production;

import java.util.List;
import java.util.Optional;

public interface SnapshotStore {

    SnapshotMetadata save(SnapshotMetadata metadata, byte[] content);

    Optional<Snapshot> load(String snapshotId);

    List<SnapshotMetadata> list(String tenantId, String agentId, String sessionId);

    boolean delete(String snapshotId);
}
