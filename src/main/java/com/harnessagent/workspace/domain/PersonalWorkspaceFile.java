package com.harnessagent.workspace.domain;

import com.harnessagent.chat.domain.ContentBlock;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

public record PersonalWorkspaceFile(
        String uri,
        String relativePath,
        String fileName,
        String mimeType,
        long size,
        Path absolutePath,
        Instant updatedAt) {

    public PersonalWorkspaceFile {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("uri is required");
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath is required");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName is required");
        }
        mimeType = mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType.trim();
        if (absolutePath == null) {
            throw new IllegalArgumentException("absolutePath is required");
        }
        absolutePath = absolutePath.toAbsolutePath().normalize();
        updatedAt = updatedAt == null ? Instant.EPOCH : updatedAt;
    }

    public ContentBlock asContentBlock() {
        return ContentBlock.file(uri, mimeType, fileName);
    }

    public Map<String, Object> referenceMetadata() {
        return Map.of(
                "uri", uri,
                "relativePath", relativePath,
                "fileName", fileName,
                "mimeType", mimeType,
                "size", size,
                "updatedAt", updatedAt.toString());
    }
}
