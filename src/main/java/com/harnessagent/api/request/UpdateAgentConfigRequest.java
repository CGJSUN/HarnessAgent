package com.harnessagent.api.request;

public record UpdateAgentConfigRequest(
        String modelProvider,
        String modelName,
        String workspace,
        Boolean compaction,
        Integer maxIters) {
}
