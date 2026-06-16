package com.harnessagent.production.sandbox;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public record SandboxExecutionPolicy(
        SandboxExecutionMode mode,
        Path workspaceRoot,
        String image,
        String remoteEndpoint,
        boolean networkEnabled,
        boolean writableFilesystem,
        Duration timeout,
        Map<String, String> metadata) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    public SandboxExecutionPolicy {
        mode = mode == null ? SandboxExecutionMode.LOCAL_PROCESS : mode;
        workspaceRoot = workspaceRoot == null ? Path.of(".").normalize() : workspaceRoot.normalize();
        image = normalize(image);
        remoteEndpoint = normalize(remoteEndpoint);
        timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
        metadata = safeMap(metadata);
        if (mode == SandboxExecutionMode.DOCKER && image.isBlank()) {
            throw new IllegalArgumentException("sandbox image is required for Docker mode");
        }
        if (mode == SandboxExecutionMode.REMOTE && remoteEndpoint.isBlank()) {
            throw new IllegalArgumentException("remote sandbox endpoint is required for remote mode");
        }
    }

    public static SandboxExecutionPolicy localProcess(Path workspaceRoot, Duration timeout) {
        return new SandboxExecutionPolicy(
                SandboxExecutionMode.LOCAL_PROCESS,
                workspaceRoot,
                "",
                "",
                false,
                true,
                timeout,
                Map.of("adapter", "local-process"));
    }

    public static SandboxExecutionPolicy docker(String image, Path workspaceRoot, Duration timeout) {
        return new SandboxExecutionPolicy(
                SandboxExecutionMode.DOCKER,
                workspaceRoot,
                image,
                "",
                false,
                true,
                timeout,
                Map.of("adapter", "docker"));
    }

    public static SandboxExecutionPolicy remote(String endpoint, Path workspaceRoot, Duration timeout) {
        return new SandboxExecutionPolicy(
                SandboxExecutionMode.REMOTE,
                workspaceRoot,
                "",
                endpoint,
                false,
                true,
                timeout,
                Map.of("adapter", "remote"));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, String> safeMap(Map<String, String> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                result.put(key.trim(), value == null ? "" : value.trim());
            }
        });
        return Map.copyOf(result);
    }
}
