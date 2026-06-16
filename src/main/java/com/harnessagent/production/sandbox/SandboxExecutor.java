package com.harnessagent.production.sandbox;

public interface SandboxExecutor {

    SandboxExecutionMode mode();

    SandboxExecutionResult execute(SandboxExecutionPolicy policy, SandboxExecutionRequest request);
}
