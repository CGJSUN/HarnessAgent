package com.harnessagent.model;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.security.SecretStore;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import org.springframework.stereotype.Component;

@Component
public class DashScopeModelProvider implements ModelProvider {

    private final HarnessAgentProperties properties;
    private final SecretStore secretStore;

    public DashScopeModelProvider(HarnessAgentProperties properties, SecretStore secretStore) {
        this.properties = properties;
        this.secretStore = secretStore;
    }

    @Override
    public String id() {
        return "dashscope";
    }

    @Override
    public Model createModel(String requestedModelName) {
        HarnessAgentProperties.ModelProviderDefinition definition =
                properties.requireModelProvider(id());
        String apiKey = resolveApiKey(definition);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DashScope API key is not configured");
        }
        String modelName = requestedModelName == null || requestedModelName.isBlank()
                ? definition.getModelName()
                : requestedModelName;
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    private String resolveApiKey(HarnessAgentProperties.ModelProviderDefinition definition) {
        if (definition.getApiKey() != null && !definition.getApiKey().isBlank()) {
            return secretStore.resolve(definition.getApiKey())
                    .orElse(definition.getApiKey());
        }
        if (definition.getApiKeyEnv() == null || definition.getApiKeyEnv().isBlank()) {
            return null;
        }
        return secretStore.resolve("env:" + definition.getApiKeyEnv()).orElse(null);
    }
}
