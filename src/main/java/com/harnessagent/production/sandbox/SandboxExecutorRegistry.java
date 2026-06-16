package com.harnessagent.production.sandbox;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SandboxExecutorRegistry {

    private final Map<SandboxExecutionMode, SandboxExecutor> executors;

    public SandboxExecutorRegistry(List<SandboxExecutor> executors) {
        EnumMap<SandboxExecutionMode, SandboxExecutor> byMode = new EnumMap<>(SandboxExecutionMode.class);
        if (executors != null) {
            executors.forEach(executor -> byMode.put(executor.mode(), executor));
        }
        this.executors = Map.copyOf(byMode);
    }

    public SandboxExecutor executor(SandboxExecutionMode mode) {
        SandboxExecutionMode effectiveMode = mode == null ? SandboxExecutionMode.LOCAL_PROCESS : mode;
        SandboxExecutor executor = executors.get(effectiveMode);
        if (executor == null) {
            throw new IllegalStateException("No sandbox executor registered for mode " + effectiveMode + ".");
        }
        return executor;
    }
}
