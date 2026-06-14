package com.harnessagent.orchestration;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ExpertAgentRegistry {

    private final Map<String, ExpertAgentDefinition> agents = new ConcurrentHashMap<>();

    public ExpertAgentDefinition register(ExpertAgentDefinition definition) {
        agents.put(definition.id(), definition);
        return definition;
    }

    public Optional<ExpertAgentDefinition> find(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    public List<ExpertAgentDefinition> list(String tenantId) {
        return agents.values().stream()
                .filter(agent -> agent.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(ExpertAgentDefinition::name))
                .toList();
    }
}
