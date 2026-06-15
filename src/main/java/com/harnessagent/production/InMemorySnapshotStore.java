package com.harnessagent.production;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySnapshotStore implements SnapshotStore {

    private final SnapshotStoreType backendType;
    private final String rootLocation;
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    public InMemorySnapshotStore(String rootLocation) {
        this(SnapshotStoreType.NONE, rootLocation);
    }

    public InMemorySnapshotStore(SnapshotStoreType backendType, String rootLocation) {
        this.backendType = backendType == null ? SnapshotStoreType.NONE : backendType;
        this.rootLocation = rootLocation == null || rootLocation.isBlank()
                ? "memory://snapshots"
                : rootLocation.replaceAll("/+$", "");
    }

    @Override
    public SnapshotMetadata save(SnapshotMetadata metadata, byte[] content) {
        SnapshotMetadata savedMetadata = metadata.withLocation(
                backendType,
                rootLocation + "/" + metadata.id());
        snapshots.put(savedMetadata.id(), new Snapshot(savedMetadata, content));
        return savedMetadata;
    }

    @Override
    public Optional<Snapshot> load(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshots.get(snapshotId.trim()));
    }

    @Override
    public List<SnapshotMetadata> list(String tenantId, String agentId, String sessionId) {
        return snapshots.values().stream()
                .map(Snapshot::metadata)
                .filter(metadata -> metadata.tenantId().equals(tenantId))
                .filter(metadata -> metadata.agentId().equals(agentId))
                .filter(metadata -> metadata.sessionId().equals(sessionId))
                .sorted(Comparator.comparing(SnapshotMetadata::createdAt))
                .toList();
    }

    @Override
    public boolean delete(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return false;
        }
        return snapshots.remove(snapshotId.trim()) != null;
    }
}
