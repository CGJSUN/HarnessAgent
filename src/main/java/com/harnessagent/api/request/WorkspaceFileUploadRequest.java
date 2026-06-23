package com.harnessagent.api.request;

public record WorkspaceFileUploadRequest(
        String agentId,
        String sessionId,
        String relativePath,
        String content,
        String mimeType) {
}
