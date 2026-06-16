package com.harnessagent.console.view;

import com.harnessagent.config.HarnessAgentProperties;

public record AgentManagementView(
        String agentId,
        String name,
        String systemPrompt,
        String modelProvider,
        String modelName,
        String workspace,
        String workloadType,
        boolean compaction,
        int maxIters) {

    public static AgentManagementView from(String agentId, HarnessAgentProperties.AgentDefinition definition) {
        return new AgentManagementView(
                agentId,
                definition.getName(),
                definition.getSystemPrompt(),
                definition.getModelProvider(),
                definition.getModelName(),
                definition.getWorkspace(),
                definition.getWorkloadType().name(),
                definition.isCompaction(),
                definition.getMaxIters());
    }
}
