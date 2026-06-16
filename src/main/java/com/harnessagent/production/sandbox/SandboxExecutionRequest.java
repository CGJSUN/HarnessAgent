package com.harnessagent.production.sandbox;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.harnessagent.production.config.AgentWorkloadType;
import com.harnessagent.runtime.RuntimeContextScope;

public record SandboxExecutionRequest(
        RuntimeContextScope context,
        AgentWorkloadType workloadType,
        String command,
        List<String> arguments,
        Path workingDirectory,
        Map<String, String> environment,
        String idempotencyKey) {

    public SandboxExecutionRequest {
        if (context == null) {
            throw new IllegalArgumentException("runtime context is required");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("sandbox command is required");
        }
        workloadType = workloadType == null ? AgentWorkloadType.UNTRUSTED : workloadType;
        command = command.trim();
        arguments = safeList(arguments);
        workingDirectory = workingDirectory == null ? Path.of(".").normalize() : workingDirectory.normalize();
        environment = safeMap(environment);
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
    }

    private static List<String> safeList(List<String> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        return input.stream()
                .filter(value -> value != null)
                .toList();
    }

    private static Map<String, String> safeMap(Map<String, String> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                result.put(key.trim(), value == null ? "" : value);
            }
        });
        return Map.copyOf(result);
    }
}
