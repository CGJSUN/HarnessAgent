package com.harnessagent.production;

import com.harnessagent.runtime.RuntimeContextScope;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceSnapshotService {

    private final ObjectProvider<SnapshotStore> snapshotStore;

    @Autowired
    public WorkspaceSnapshotService(ObjectProvider<SnapshotStore> snapshotStore) {
        this.snapshotStore = snapshotStore;
    }

    WorkspaceSnapshotService(SnapshotStore snapshotStore) {
        this.snapshotStore = new StaticSnapshotStoreProvider(snapshotStore);
    }

    public Optional<SnapshotMetadata> save(
            RuntimeContextScope context,
            WorkspacePlan workspacePlan,
            Path workspace,
            String taskId) {
        if (workspacePlan == null || !workspacePlan.snapshotStore().enabled()) {
            return Optional.empty();
        }
        SnapshotStore store = snapshotStore.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException("Snapshot store is enabled but no SnapshotStore bean is active.");
        }
        SnapshotMetadata metadata = new SnapshotMetadata(
                null,
                context.tenantId(),
                context.agentId(),
                context.sessionId(),
                taskId,
                Instant.now(),
                workspacePlan.snapshotStore().type(),
                workspacePlan.snapshotStore().uri());
        return Optional.of(store.save(metadata, zipWorkspace(workspace)));
    }

    public Optional<SnapshotMetadata> restore(
            RuntimeContextScope context,
            String snapshotId,
            Path workspace) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return Optional.empty();
        }
        SnapshotStore store = snapshotStore.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException("Snapshot restore requested but no SnapshotStore bean is active.");
        }
        Snapshot snapshot = store.load(snapshotId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Unknown snapshot: " + snapshotId));
        authorize(context, snapshot.metadata());
        unzipWorkspace(snapshot.content(), workspace);
        return Optional.of(snapshot.metadata());
    }

    private static byte[] zipWorkspace(Path workspace) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(bytes)) {
            if (workspace != null && Files.isDirectory(workspace)) {
                try (var paths = Files.walk(workspace)) {
                    for (Path path : paths.filter(Files::isRegularFile).toList()) {
                        Path relative = workspace.relativize(path);
                        zip.putNextEntry(new ZipEntry(relative.toString().replace('\\', '/')));
                        Files.copy(path, zip);
                        zip.closeEntry();
                    }
                }
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to snapshot workspace: " + ex.getMessage(), ex);
        }
    }

    private static void unzipWorkspace(byte[] content, Path workspace) {
        try {
            Files.createDirectories(workspace);
            Path root = workspace.toAbsolutePath().normalize();
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    Path target = root.resolve(entry.getName()).normalize();
                    if (!target.startsWith(root)) {
                        throw new IllegalStateException("Snapshot contains an unsafe path: " + entry.getName());
                    }
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    zip.closeEntry();
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to restore workspace: " + ex.getMessage(), ex);
        }
    }

    private static void authorize(RuntimeContextScope context, SnapshotMetadata metadata) {
        if (!metadata.tenantId().equals(context.tenantId())
                || !metadata.agentId().equals(context.agentId())
                || !metadata.sessionId().equals(context.sessionId())) {
            throw new SecurityException("Snapshot does not belong to the requested tenant, Agent, and session.");
        }
    }

    private record StaticSnapshotStoreProvider(SnapshotStore snapshotStore) implements ObjectProvider<SnapshotStore> {

        @Override
        public SnapshotStore getObject(Object... args) {
            return snapshotStore;
        }

        @Override
        public SnapshotStore getIfAvailable() {
            return snapshotStore;
        }

        @Override
        public SnapshotStore getIfUnique() {
            return snapshotStore;
        }

        @Override
        public SnapshotStore getObject() {
            return snapshotStore;
        }
    }
}
