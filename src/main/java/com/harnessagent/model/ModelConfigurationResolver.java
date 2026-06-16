package com.harnessagent.model;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.production.budget.BudgetLimit;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ModelConfigurationResolver {

    private final HarnessAgentProperties properties;
    private final ProductionRuntimeProperties runtimeProperties;

    public ModelConfigurationResolver(
            HarnessAgentProperties properties,
            ProductionRuntimeProperties runtimeProperties) {
        this.properties = properties;
        this.runtimeProperties = runtimeProperties;
    }

    public ModelSelection resolve(String agentId) {
        HarnessAgentProperties.AgentDefinition agent = properties.getAgents().get(agentId);
        String providerId = firstNonBlank(
                agent == null ? null : agent.getModelProvider(),
                properties.getDefaultProvider(),
                "echo");
        return resolve(agent, providerId, true);
    }

    public ModelSelection resolveFallback(String agentId, String providerId) {
        HarnessAgentProperties.AgentDefinition agent = properties.getAgents().get(agentId);
        return resolve(agent, providerId, false);
    }

    private ModelSelection resolve(
            HarnessAgentProperties.AgentDefinition agent,
            String providerId,
            boolean primaryProvider) {
        String effectiveProviderId = firstNonBlank(providerId, properties.getDefaultProvider(), "echo");
        HarnessAgentProperties.ModelProviderDefinition provider =
                properties.getModelProviders().get(effectiveProviderId);
        String modelName = firstNonBlank(
                primaryProvider && agent != null ? agent.getModelName() : null,
                provider == null ? null : provider.getModelName());
        String apiKeyRef = firstNonBlank(
                primaryProvider && agent != null ? agent.getModelApiKeyRef() : null,
                provider == null ? null : provider.getApiKeyRef(),
                provider == null || provider.getApiKeyEnv() == null || provider.getApiKeyEnv().isBlank()
                        ? null
                        : "env:" + provider.getApiKeyEnv().trim());
        return new ModelSelection(
                effectiveProviderId,
                firstNonBlank(provider == null ? null : provider.getType(), effectiveProviderId),
                modelName,
                apiKeyRef,
                budgetLimit(agent),
                primaryProvider && agent != null ? configuredFallbacks(agent.getFallbackProviders()) : List.of());
    }

    private BudgetLimit budgetLimit(HarnessAgentProperties.AgentDefinition agent) {
        if (agent == null || agent.getBudget() == null) {
            return null;
        }
        Long requestLimit = agent.getBudget().getRequestLimit();
        Long tokenLimit = agent.getBudget().getTokenLimit();
        if (requestLimit == null && tokenLimit == null) {
            return null;
        }
        return new BudgetLimit(
                requestLimit == null ? runtimeProperties.getBudget().getRequestLimit() : requestLimit,
                tokenLimit == null ? runtimeProperties.getBudget().getTokenLimit() : tokenLimit);
    }

    private static List<String> configuredFallbacks(List<String> providers) {
        if (providers == null || providers.isEmpty()) {
            return List.of();
        }
        return providers.stream()
                .filter(provider -> provider != null && !provider.isBlank())
                .map(String::trim)
                .toList();
    }

    private static String firstNonBlank(String first, String... rest) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (rest == null) {
            return null;
        }
        for (String value : rest) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
