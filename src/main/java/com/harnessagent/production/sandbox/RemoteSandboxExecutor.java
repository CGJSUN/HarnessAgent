package com.harnessagent.production.sandbox;

import org.springframework.stereotype.Component;

@Component
public class RemoteSandboxExecutor implements SandboxExecutor {

    @Override
    public SandboxExecutionMode mode() {
        return SandboxExecutionMode.REMOTE;
    }

    @Override
    public SandboxExecutionResult execute(SandboxExecutionPolicy policy, SandboxExecutionRequest request) {
        return SandboxExecutionResult.unsupported(
                mode(),
                "Remote sandbox runner is an adapter point and is not wired to execute commands yet.");
    }
}
