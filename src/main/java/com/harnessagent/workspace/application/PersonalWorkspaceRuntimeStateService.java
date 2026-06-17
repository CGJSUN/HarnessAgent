package com.harnessagent.workspace.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.production.snapshot.SnapshotMetadata;
import com.harnessagent.production.workspace.WorkspaceSnapshotService;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.workspace.domain.PersonalWorkspaceLayout;
import com.harnessagent.workspace.domain.PersonalWorkspaceRuntimeState;
import com.harnessagent.workspace.domain.WorkspaceSnapshotReference;
import com.harnessagent.workspace.domain.WorkspaceSnapshotReferenceType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PersonalWorkspaceRuntimeStateService {

    private static final String RUNTIME_STATE_FILE = "runtime.json";

    private final PersonalWorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public PersonalWorkspaceRuntimeStateService(PersonalWorkspaceService workspaceService) {
        this(workspaceService, new ObjectMapper().findAndRegisterModules());
    }

    PersonalWorkspaceRuntimeStateService(PersonalWorkspaceService workspaceService, ObjectMapper objectMapper) {
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
    }

    public PersonalWorkspaceRuntimeState saveReference(
            RuntimeContextScope context,
            WorkspaceSnapshotReferenceType type,
            SnapshotMetadata metadata,
            String taskId,
            String planPath) {
        PersonalWorkspaceRuntimeState current = load(context)
                .orElseGet(() -> PersonalWorkspaceRuntimeState.empty(
                        context.userId(),
                        context.agentId(),
                        context.sessionId()));
        PersonalWorkspaceRuntimeState next = current.withSnapshot(
                type,
                WorkspaceSnapshotReference.from(type, metadata),
                taskId,
                planPath);
        write(context, next);
        return next;
    }

    public Optional<PersonalWorkspaceRuntimeState> load(RuntimeContextScope context) {
        Path stateFile = stateFile(workspaceService.initialize(context), context);
        if (!Files.isRegularFile(stateFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(stateFile.toFile(), PersonalWorkspaceRuntimeState.class));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read personal workspace runtime state.", ex);
        }
    }

    public Optional<SnapshotMetadata> restoreWorkspaceSnapshot(
            RuntimeContextScope context,
            WorkspaceSnapshotService snapshotService) {
        return restoreSnapshot(context, WorkspaceSnapshotReferenceType.WORKSPACE, snapshotService);
    }

    public Optional<SnapshotMetadata> restoreSandboxSnapshot(
            RuntimeContextScope context,
            WorkspaceSnapshotService snapshotService) {
        return restoreSnapshot(context, WorkspaceSnapshotReferenceType.SANDBOX, snapshotService);
    }

    private Optional<SnapshotMetadata> restoreSnapshot(
            RuntimeContextScope context,
            WorkspaceSnapshotReferenceType type,
            WorkspaceSnapshotService snapshotService) {
        PersonalWorkspaceRuntimeState state = load(context).orElse(null);
        if (state == null || snapshotService == null) {
            return Optional.empty();
        }
        WorkspaceSnapshotReference reference = type == WorkspaceSnapshotReferenceType.WORKSPACE
                ? state.workspaceSnapshot()
                : state.sandboxSnapshot();
        if (reference == null) {
            return Optional.empty();
        }
        PersonalWorkspaceLayout layout = workspaceService.initialize(context);
        Path restoreTarget = type == WorkspaceSnapshotReferenceType.WORKSPACE
                ? layout.root()
                : sessionDirectory(layout, context).resolve("sandbox");
        return snapshotService.restore(context, reference.snapshotId(), restoreTarget);
    }

    private void write(RuntimeContextScope context, PersonalWorkspaceRuntimeState state) {
        Path stateFile = stateFile(workspaceService.initialize(context), context);
        try {
            Files.createDirectories(stateFile.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), state);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to write personal workspace runtime state.", ex);
        }
    }

    private static Path stateFile(PersonalWorkspaceLayout layout, RuntimeContextScope context) {
        return sessionDirectory(layout, context).resolve(RUNTIME_STATE_FILE).toAbsolutePath().normalize();
    }

    private static Path sessionDirectory(PersonalWorkspaceLayout layout, RuntimeContextScope context) {
        return layout.sessionsDirectory().resolve("session-" + sha256(context.sessionId()).substring(0, 16));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available.", ex);
        }
    }
}
