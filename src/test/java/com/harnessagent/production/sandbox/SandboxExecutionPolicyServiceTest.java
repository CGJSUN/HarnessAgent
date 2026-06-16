package com.harnessagent.production.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.harnessagent.production.config.AgentWorkloadType;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.config.RuntimeProfile;
import com.harnessagent.runtime.RuntimeContextScope;

class SandboxExecutionPolicyServiceTest {

    @TempDir
    Path workspaceRoot;

    private final RuntimeContextScope context = new RuntimeContextScope(
            "personal:owner-a",
            "owner-a",
            "agent-a",
            "session-a",
            "owner-a",
            "session-a");

    @Test
    void choosesLocalProcessAdapterForDevelopmentHighRiskWorkloads() {
        ProductionRuntimeProperties properties = new ProductionRuntimeProperties();
        properties.getTimeouts().setSandboxTimeout(Duration.ofSeconds(30));
        SandboxExecutionPolicyService service = new SandboxExecutionPolicyService(properties);

        SandboxExecutionPolicy policy = service.policyFor(context, AgentWorkloadType.SHELL, workspaceRoot);

        assertThat(policy.mode()).isEqualTo(SandboxExecutionMode.LOCAL_PROCESS);
        assertThat(policy.workspaceRoot()).isEqualTo(workspaceRoot.normalize());
        assertThat(policy.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.metadata()).containsEntry("adapter", "local-process");
    }

    @Test
    void choosesDockerAdapterForProductionHighRiskWorkloadsByDefault() {
        ProductionRuntimeProperties properties = new ProductionRuntimeProperties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        properties.getSandbox().setEnabled(true);
        properties.getSandbox().setImage("personal-agent-sandbox:latest");
        properties.getSandbox().setWorkspaceRoot("/sandbox/workspace");
        SandboxExecutionPolicyService service = new SandboxExecutionPolicyService(properties);

        SandboxExecutionPolicy policy = service.policyFor(context, AgentWorkloadType.CODE, workspaceRoot);

        assertThat(policy.mode()).isEqualTo(SandboxExecutionMode.DOCKER);
        assertThat(policy.image()).isEqualTo("personal-agent-sandbox:latest");
        assertThat(policy.workspaceRoot()).isEqualTo(Path.of("/sandbox/workspace"));
        assertThat(policy.remoteEndpoint()).isBlank();
    }

    @Test
    void choosesRemoteAdapterWhenProductionSandboxModeIsRemote() {
        ProductionRuntimeProperties properties = new ProductionRuntimeProperties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        properties.getSandbox().setEnabled(true);
        properties.getSandbox().setMode(SandboxExecutionMode.REMOTE);
        properties.getSandbox().setRemoteEndpoint("https://sandbox.internal/run");
        SandboxExecutionPolicyService service = new SandboxExecutionPolicyService(properties);

        SandboxExecutionPolicy policy = service.policyFor(context, AgentWorkloadType.UNTRUSTED, workspaceRoot);

        assertThat(policy.mode()).isEqualTo(SandboxExecutionMode.REMOTE);
        assertThat(policy.remoteEndpoint()).isEqualTo("https://sandbox.internal/run");
        assertThat(policy.metadata()).containsEntry("adapter", "remote");
    }

    @Test
    void rejectsProductionOfficeWorkloadsFromSandboxExecutionPolicy() {
        ProductionRuntimeProperties properties = new ProductionRuntimeProperties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        SandboxExecutionPolicyService service = new SandboxExecutionPolicyService(properties);

        assertThatThrownBy(() -> service.policyFor(context, AgentWorkloadType.OFFICE, workspaceRoot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not require sandbox execution");
    }

    @Test
    void rejectsLocalProcessAdapterForProductionHighRiskWorkloads() {
        ProductionRuntimeProperties properties = new ProductionRuntimeProperties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        properties.getSandbox().setEnabled(true);
        properties.getSandbox().setMode(SandboxExecutionMode.LOCAL_PROCESS);
        SandboxExecutionPolicyService service = new SandboxExecutionPolicyService(properties);

        assertThatThrownBy(() -> service.policyFor(context, AgentWorkloadType.SQL, workspaceRoot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot use local process");
    }

    @Test
    void rejectsUnsafeFallbackWorkspaceSegments() {
        ProductionRuntimeProperties properties = new ProductionRuntimeProperties();
        SandboxExecutionPolicyService service = new SandboxExecutionPolicyService(properties);
        RuntimeContextScope unsafe = new RuntimeContextScope(
                "personal:owner-a",
                "../owner-a",
                "../agent-a",
                "session-a",
                "../owner-a",
                "session-a");

        assertThatThrownBy(() -> service.policyFor(unsafe, AgentWorkloadType.SHELL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safe path segment");
    }

    @Test
    void normalizesSandboxWorkspaceRootConfiguration() {
        ProductionRuntimeProperties properties = new ProductionRuntimeProperties();

        properties.getSandbox().setWorkspaceRoot(null);
        assertThat(properties.getSandbox().getWorkspaceRoot()).isEqualTo("/workspace");
        properties.getSandbox().setWorkspaceRoot("  ");
        assertThat(properties.getSandbox().getWorkspaceRoot()).isEqualTo("/workspace");
        assertThatThrownBy(() -> properties.getSandbox().setWorkspaceRoot("relative/workspace"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute path");
    }

    @Test
    void exposesLocalDockerAndRemoteExecutorAdapterPoints() {
        SandboxExecutorRegistry registry = new SandboxExecutorRegistry(List.of(
                new LocalProcessSandboxExecutor(),
                new DockerSandboxExecutor(),
                new RemoteSandboxExecutor()));
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.localProcess(workspaceRoot, Duration.ofSeconds(5));
        SandboxExecutionRequest request = new SandboxExecutionRequest(
                context,
                AgentWorkloadType.SHELL,
                "echo",
                List.of("hello"),
                workspaceRoot,
                Map.of(),
                "idem-1");

        assertUnsupported(registry, SandboxExecutionMode.LOCAL_PROCESS, policy, request);
        assertUnsupported(registry, SandboxExecutionMode.DOCKER, policy, request);
        assertUnsupported(registry, SandboxExecutionMode.REMOTE, policy, request);
    }

    private static void assertUnsupported(
            SandboxExecutorRegistry registry,
            SandboxExecutionMode mode,
            SandboxExecutionPolicy policy,
            SandboxExecutionRequest request) {
        SandboxExecutionResult result = registry.executor(mode).execute(policy, request);

        assertThat(result.status()).isEqualTo(SandboxExecutionStatus.UNSUPPORTED);
        assertThat(result.metadata()).containsEntry("mode", mode.name());
    }
}
