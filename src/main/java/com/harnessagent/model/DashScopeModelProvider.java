package com.harnessagent.model;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.security.persistence.SecretStore;
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
        return createModel(new ModelProviderRequest(id(), requestedModelName, null));
    }

    @Override
    public Model createModel(ModelProviderRequest request) {
        String providerId = request == null || request.providerId() == null || request.providerId().isBlank()
                ? id()
                : request.providerId().trim();
        HarnessAgentProperties.ModelProviderDefinition definition =
                properties.requireModelProvider(providerId);
        String apiKey = resolveApiKey(definition, request);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DashScope API key is not configured");
        }
        String requestedModelName = request == null ? null : request.modelName();
        String modelName = requestedModelName == null || requestedModelName.isBlank()
                ? definition.getModelName()
                : requestedModelName;
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    private String resolveApiKey(
            HarnessAgentProperties.ModelProviderDefinition definition,
            ModelProviderRequest request) {
        String requestRef = request == null ? null : request.apiKeyRef();
        if (requestRef != null && !requestRef.isBlank()) {
            return secretStore.resolve(requestRef.trim()).orElse(null);
        }
        if (definition.getApiKeyRef() != null && !definition.getApiKeyRef().isBlank()) {
            return secretStore.resolve(definition.getApiKeyRef().trim()).orElse(null);
        }
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
