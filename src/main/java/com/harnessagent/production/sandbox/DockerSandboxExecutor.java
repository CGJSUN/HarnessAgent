package com.harnessagent.production.sandbox;

import org.springframework.stereotype.Component;

@Component
public class DockerSandboxExecutor implements SandboxExecutor {

    @Override
    public SandboxExecutionMode mode() {
        return SandboxExecutionMode.DOCKER;
    }

    @Override
    public SandboxExecutionResult execute(SandboxExecutionPolicy policy, SandboxExecutionRequest request) {
        return SandboxExecutionResult.unsupported(
                mode(),
                "Docker sandbox runner is an adapter point and is not wired to execute commands yet.");
    }
}
