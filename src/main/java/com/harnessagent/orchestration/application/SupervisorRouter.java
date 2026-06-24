package com.harnessagent.orchestration.application;

import com.harnessagent.security.domain.OwnerPrincipal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.harnessagent.orchestration.domain.ExpertAgentDefinition;
import com.harnessagent.orchestration.domain.OrchestrationStatus;
import com.harnessagent.orchestration.domain.RouteDecision;

@Component
public class SupervisorRouter {

    private static final double MIN_CONFIDENCE = 0.5d;

    public RouteDecision route(
            OwnerPrincipal principal,
            String taskIntent,
            List<ExpertAgentDefinition> candidates) {
        return route(principal, taskIntent, Map.of(), candidates);
    }

    public RouteDecision route(
            OwnerPrincipal principal,
            String taskIntent,
            Map<String, Object> context,
            List<ExpertAgentDefinition> candidates) {
        List<ExpertAgentDefinition> permitted = candidates.stream()
                .filter(ExpertAgentDefinition::approved)
                .filter(ExpertAgentDefinition::enabled)
                .filter(agent -> agent.allowedOwnerIds().isEmpty()
                        || agent.allowedOwnerIds().contains(principal.ownerId()))
                .toList();
        ExpertAgentDefinition selected = permitted.stream()
                .max(Comparator.comparing(agent -> score(agent, taskIntent, context)))
                .orElse(null);
        double confidence = selected == null ? 0d : score(selected, taskIntent, context);
        if (selected == null || confidence < MIN_CONFIDENCE) {
            return new RouteDecision("", confidence, OrchestrationStatus.ESCALATED,
                    "No permitted expert agent reached routing confidence threshold.");
        }
        return new RouteDecision(selected.id(), confidence, OrchestrationStatus.ROUTED, "routed");
    }

    private static double score(ExpertAgentDefinition agent, String taskIntent, Map<String, Object> context) {
        double score = agent.canHandle(taskIntent) ? 0.9d : 0.2d;
        if (agent.canHandle(taskIntent)) {
            score -= Math.min(0.12d, privilegeSurface(agent) * 0.01d);
            score -= Math.min(0.3d, agent.contextBoundary().blockedCategoryCount(context) * 0.15d);
        }
        return score;
    }

    private static int privilegeSurface(ExpertAgentDefinition agent) {
        return agent.allowedTools().size()
                + agent.allowedSkills().size()
                + agent.allowedKnowledgeSources().size();
    }
}
