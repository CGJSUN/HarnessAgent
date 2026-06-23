package com.harnessagent.api.response;

public record WorkspaceFilePreviewResponse(
        WorkspaceFileResponse file,
        String content,
        boolean truncated) {
}
