package com.harnessagent.production.workspace;

import com.harnessagent.runtime.RuntimeContextScope;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.harnessagent.production.snapshot.Snapshot;
import com.harnessagent.production.snapshot.SnapshotMetadata;
import com.harnessagent.production.snapshot.SnapshotStore;

@Service
public class WorkspaceSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceSnapshotService.class);

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
            log.error("workspace snapshot failed reason={}", "store_missing");
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
        SnapshotMetadata saved = store.save(metadata, zipWorkspace(workspace));
        log.info("workspace snapshot saved tenantId={} agentId={} sessionHash={} backendType={}",
                context.tenantId(), context.agentId(), com.harnessagent.security.application.SafeLogFields.session(context.sessionId()),
                saved.backendType());
        return Optional.of(saved);
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
            log.error("workspace snapshot restore failed reason={}", "store_missing");
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
                    for (Path path : paths
                            .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                            .toList()) {
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
            rejectSymlink(root, root);
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
                    rejectSymlink(root, target.getParent());
                    if (Files.isSymbolicLink(target)) {
                        throw new IllegalStateException("Snapshot restore target is a symbolic link: " + entry.getName());
                    }
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                    zip.closeEntry();
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to restore workspace: " + ex.getMessage(), ex);
        }
    }

    private static void rejectSymlink(Path root, Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalStateException("Snapshot path escapes workspace.");
        }
        Path current = root;
        Path relative = root.relativize(normalized);
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalStateException("Snapshot path contains a symbolic link.");
            }
        }
    }

    private static void authorize(RuntimeContextScope context, SnapshotMetadata metadata) {
        // Restores are scoped to tenant, Agent, and session; a valid snapshot id alone is not sufficient authority.
        if (!metadata.tenantId().equals(context.tenantId())
                || !metadata.agentId().equals(context.agentId())
                || !metadata.sessionId().equals(context.sessionId())) {
            log.warn("workspace snapshot restore rejected tenantId={} agentId={} sessionHash={} reason={}",
                    context.tenantId(), context.agentId(), com.harnessagent.security.application.SafeLogFields.session(context.sessionId()),
                    "context_mismatch");
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
