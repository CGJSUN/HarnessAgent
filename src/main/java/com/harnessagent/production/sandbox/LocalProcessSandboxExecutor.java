package com.harnessagent.production.sandbox;

import org.springframework.stereotype.Component;

@Component
public class LocalProcessSandboxExecutor implements SandboxExecutor {

    @Override
    public SandboxExecutionMode mode() {
        return SandboxExecutionMode.LOCAL_PROCESS;
    }

    @Override
    public SandboxExecutionResult execute(SandboxExecutionPolicy policy, SandboxExecutionRequest request) {
        return SandboxExecutionResult.unsupported(
                mode(),
                "Local process sandbox runner is an adapter point and is not wired to execute commands yet.");
    }
}
