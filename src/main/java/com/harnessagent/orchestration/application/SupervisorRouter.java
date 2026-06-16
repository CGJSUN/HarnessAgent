package com.harnessagent.orchestration.application;

import com.harnessagent.security.domain.SecurityPrincipal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import com.harnessagent.orchestration.domain.ExpertAgentDefinition;
import com.harnessagent.orchestration.domain.OrchestrationStatus;
import com.harnessagent.orchestration.domain.RouteDecision;

@Component
public class SupervisorRouter {

    private static final double MIN_CONFIDENCE = 0.5d;

    public RouteDecision route(
            SecurityPrincipal principal,
            String taskIntent,
            List<ExpertAgentDefinition> candidates) {
        List<ExpertAgentDefinition> permitted = candidates.stream()
                .filter(ExpertAgentDefinition::approved)
                .filter(ExpertAgentDefinition::enabled)
                .filter(agent -> agent.requiredRoles().isEmpty()
                        || agent.requiredRoles().stream().anyMatch(principal.roles()::contains))
                .toList();
        ExpertAgentDefinition selected = permitted.stream()
                .max(Comparator.comparing(agent -> score(agent, taskIntent)))
                .orElse(null);
        double confidence = selected == null ? 0d : score(selected, taskIntent);
        if (selected == null || confidence < MIN_CONFIDENCE) {
            return new RouteDecision("", confidence, OrchestrationStatus.ESCALATED,
                    "No permitted expert agent reached routing confidence threshold.");
        }
        return new RouteDecision(selected.id(), confidence, OrchestrationStatus.ROUTED, "routed");
    }

    private static double score(ExpertAgentDefinition agent, String taskIntent) {
        if (agent.canHandle(taskIntent)) {
            return 0.9d;
        }
        return 0.2d;
    }
}
