package com.harnessagent.production;

import org.springframework.stereotype.Service;

@Service
public class WorkspacePolicyService {

    private final ProductionRuntimeProperties properties;

    public WorkspacePolicyService(ProductionRuntimeProperties properties) {
        this.properties = properties;
    }

    public WorkspacePlan plan(String agentId, AgentWorkloadType workloadType, String localWorkspace) {
        AgentWorkloadType effectiveType = workloadType == null ? AgentWorkloadType.OFFICE : workloadType;
        if (!properties.isProduction()) {
            return new WorkspacePlan(
                    WorkspaceMode.LOCAL,
                    localWorkspace == null || localWorkspace.isBlank()
                            ? ".harness-agent/workspaces/" + agentId
                            : localWorkspace.trim(),
                    "",
                    snapshotPlan());
        }
        if (requiresSandbox(effectiveType)) {
            if (!properties.getSandbox().isEnabled()) {
                throw new IllegalStateException("Production code, shell, SQL, or untrusted workload requires sandbox.");
            }
            if (properties.getSnapshotStore().getType() == SnapshotStoreType.NONE) {
                throw new IllegalStateException("Sandbox workspace requires snapshot store.");
            }
            return new WorkspacePlan(
                    WorkspaceMode.SANDBOX,
                    properties.getSandbox().getWorkspaceRoot(),
                    properties.getSandbox().getImage(),
                    snapshotPlan());
        }
        return new WorkspacePlan(
                WorkspaceMode.REMOTE,
                properties.getRemoteFilesystem().getRootUri() + "/" + agentId,
                "",
                snapshotPlan());
    }

    public SnapshotStorePlan snapshotPlan() {
        return new SnapshotStorePlan(
                properties.getSnapshotStore().getType(),
                properties.getSnapshotStore().getUri());
    }

    private static boolean requiresSandbox(AgentWorkloadType workloadType) {
        return workloadType == AgentWorkloadType.CODE
                || workloadType == AgentWorkloadType.SHELL
                || workloadType == AgentWorkloadType.SQL
                || workloadType == AgentWorkloadType.UNTRUSTED;
    }
}
