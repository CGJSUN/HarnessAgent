package com.harnessagent.api.response;

import com.harnessagent.workspace.domain.PersonalWorkspaceFile;
import java.time.Instant;

public record WorkspaceFileResponse(
        String uri,
        String relativePath,
        String fileName,
        String mimeType,
        long size,
        Instant updatedAt) {

    public static WorkspaceFileResponse from(PersonalWorkspaceFile file) {
        return new WorkspaceFileResponse(
                file.uri(),
                file.relativePath(),
                file.fileName(),
                file.mimeType(),
                file.size(),
                file.updatedAt());
    }
}
