package com.harnessagent.skill.domain;

import com.harnessagent.security.domain.SecurityPrincipal;
import java.util.Map;
import java.util.Set;

public record SkillExecutionRequest(
        SecurityPrincipal principal,
        String agentId,
        String taskIntent,
        String task,
        Set<String> requestedTools,
        Set<String> requestedFiles,
        boolean networkRequested,
        boolean sandboxRequested,
        boolean memoryRequested,
        Map<String, Object> context) {

    public SkillExecutionRequest {
        if (principal == null) {
            throw new IllegalArgumentException("principal is required");
        }
        agentId = agentId == null ? "" : agentId.trim();
        taskIntent = taskIntent == null ? "" : taskIntent.trim();
        task = task == null ? "" : task.trim();
        requestedTools = requestedTools == null ? Set.of() : Set.copyOf(requestedTools);
        requestedFiles = requestedFiles == null ? Set.of() : Set.copyOf(requestedFiles);
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
