package com.harnessagent.production.sandbox;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record SandboxExecutionResult(
        SandboxExecutionStatus status,
        int exitCode,
        String stdout,
        String stderr,
        String message,
        Instant completedAt,
        Map<String, String> metadata) {

    public SandboxExecutionResult {
        status = status == null ? SandboxExecutionStatus.FAILED : status;
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
        message = message == null ? "" : message.trim();
        completedAt = completedAt == null ? Instant.now() : completedAt;
        metadata = safeMap(metadata);
    }

    public static SandboxExecutionResult unsupported(SandboxExecutionMode mode, String reason) {
        return new SandboxExecutionResult(
                SandboxExecutionStatus.UNSUPPORTED,
                -1,
                "",
                "",
                reason,
                Instant.now(),
                Map.of("mode", mode.name()));
    }

    public static SandboxExecutionResult rejected(String reason) {
        return new SandboxExecutionResult(
                SandboxExecutionStatus.REJECTED,
                -1,
                "",
                "",
                reason,
                Instant.now(),
                Map.of());
    }

    public static SandboxExecutionResult succeeded(int exitCode, String stdout, String stderr, Map<String, String> metadata) {
        return new SandboxExecutionResult(
                SandboxExecutionStatus.SUCCEEDED,
                exitCode,
                stdout,
                stderr,
                "Sandbox execution completed.",
                Instant.now(),
                metadata);
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
