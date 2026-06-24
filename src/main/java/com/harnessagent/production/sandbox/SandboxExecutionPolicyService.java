package com.harnessagent.production.sandbox;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.stereotype.Service;
import com.harnessagent.production.config.AgentWorkloadType;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.runtime.RuntimeContextScope;

@Service
public class SandboxExecutionPolicyService {

    private final ProductionRuntimeProperties properties;

    public SandboxExecutionPolicyService(ProductionRuntimeProperties properties) {
        this.properties = properties;
    }

    public SandboxExecutionPolicy policyFor(
            RuntimeContextScope context,
            AgentWorkloadType workloadType,
            Path authorizedWorkspaceRoot) {
        if (context == null) {
            throw new IllegalArgumentException("runtime context is required");
        }
        AgentWorkloadType effectiveType = workloadType == null ? AgentWorkloadType.UNTRUSTED : workloadType;
        Path workspaceRoot = workspaceRoot(context, authorizedWorkspaceRoot);
        Duration timeout = properties.getTimeouts().getSandboxTimeout();
        if (!requiresSandbox(effectiveType)) {
            if (properties.isProduction()) {
                throw new IllegalStateException(
                        "Production workload does not require sandbox execution; use the governed tool executor.");
            }
            return SandboxExecutionPolicy.localProcess(workspaceRoot, timeout);
        }
        if (!properties.isProduction()) {
            return SandboxExecutionPolicy.localProcess(workspaceRoot, timeout);
        }
        if (!properties.getSandbox().isEnabled()) {
            throw new IllegalStateException("Production code, shell, SQL, or untrusted workload requires sandbox.");
        }
        SandboxExecutionMode mode = properties.getSandbox().getMode();
        return switch (mode) {
            case LOCAL_PROCESS -> throw new IllegalStateException(
                    "Production high-risk workload cannot use local process sandbox mode.");
            case DOCKER -> SandboxExecutionPolicy.docker(
                    properties.getSandbox().getImage(),
                    Path.of(properties.getSandbox().getWorkspaceRoot()),
                    timeout);
            case REMOTE -> SandboxExecutionPolicy.remote(
                    properties.getSandbox().getRemoteEndpoint(),
                    Path.of(properties.getSandbox().getWorkspaceRoot()),
                    timeout);
        };
    }

    public static boolean requiresSandbox(AgentWorkloadType workloadType) {
        AgentWorkloadType effectiveType = workloadType == null ? AgentWorkloadType.UNTRUSTED : workloadType;
        return effectiveType == AgentWorkloadType.CODE
                || effectiveType == AgentWorkloadType.SHELL
                || effectiveType == AgentWorkloadType.SQL
                || effectiveType == AgentWorkloadType.UNTRUSTED;
    }

    private static Path workspaceRoot(RuntimeContextScope context, Path authorizedWorkspaceRoot) {
        if (authorizedWorkspaceRoot != null) {
            return authorizedWorkspaceRoot.normalize();
        }
        Path baseRoot = Path.of(".harness-agent", "personal", "workspaces").normalize();
        Path root = baseRoot
                .resolve(safePathSegment(context.agentId(), "agentId"))
                .resolve(safePathSegment(context.ownerId(), "userId"))
                .normalize();
        if (!root.startsWith(baseRoot)) {
            throw new IllegalArgumentException("sandbox workspace path escapes personal workspace root");
        }
        return root;
    }

    private static String safePathSegment(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required for sandbox workspace path");
        }
        String segment = value.trim();
        if (segment.equals(".")
                || segment.equals("..")
                || segment.contains("/")
                || segment.contains("\\")
                || Path.of(segment).getNameCount() != 1) {
            throw new IllegalArgumentException(field + " must be a single safe path segment");
        }
        return segment;
    }
}
