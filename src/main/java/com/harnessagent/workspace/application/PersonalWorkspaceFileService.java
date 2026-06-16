package com.harnessagent.workspace.application;

import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.workspace.domain.PersonalWorkspaceFile;
import com.harnessagent.workspace.domain.PersonalWorkspaceLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class PersonalWorkspaceFileService {

    private static final String WORKSPACE_URI_PREFIX = "workspace://";

    private final PersonalWorkspaceService workspaceService;

    public PersonalWorkspaceFileService(PersonalWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    public PersonalWorkspaceFile saveGeneratedFile(
            RuntimeContextScope context,
            String relativePath,
            byte[] content,
            String mimeType) {
        return save(context, relativePath, content, mimeType);
    }

    public PersonalWorkspaceFile upload(
            RuntimeContextScope context,
            String relativePath,
            byte[] content,
            String mimeType) {
        return save(context, relativePath, content, mimeType);
    }

    public byte[] download(RuntimeContextScope context, String workspaceUriOrPath) {
        Path path = workspaceService.resolveAuthorizedPath(context, workspaceUriOrPath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("workspace file does not exist");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read workspace file", ex);
        }
    }

    public boolean delete(RuntimeContextScope context, String workspaceUriOrPath) {
        Path path = workspaceService.resolveAuthorizedPath(context, workspaceUriOrPath);
        try {
            return Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to delete workspace file", ex);
        }
    }

    public PersonalWorkspaceFile locate(RuntimeContextScope context, String workspaceUriOrPath, String mimeType) {
        Path path = workspaceService.resolveAuthorizedPath(context, workspaceUriOrPath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("workspace file does not exist");
        }
        return describe(context, path, mimeType);
    }

    private PersonalWorkspaceFile save(
            RuntimeContextScope context,
            String relativePath,
            byte[] content,
            String mimeType) {
        workspaceService.initialize(context);
        Path path = workspaceService.resolveAuthorizedPath(context, relativePath);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, content == null ? new byte[0] : content);
            return describe(context, path, mimeType);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save workspace file", ex);
        }
    }

    private PersonalWorkspaceFile describe(RuntimeContextScope context, Path absolutePath, String mimeType) {
        PersonalWorkspaceLayout layout = workspaceService.layout(context);
        Path root = layout.root();
        Path normalized = absolutePath.toAbsolutePath().normalize();
        String relativePath = root.relativize(normalized).toString().replace('\\', '/');
        try {
            return new PersonalWorkspaceFile(
                    WORKSPACE_URI_PREFIX + relativePath,
                    relativePath,
                    normalized.getFileName().toString(),
                    mimeType,
                    Files.size(normalized),
                    normalized,
                    Instant.ofEpochMilli(Files.getLastModifiedTime(normalized).toMillis()));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to describe workspace file", ex);
        }
    }
}
