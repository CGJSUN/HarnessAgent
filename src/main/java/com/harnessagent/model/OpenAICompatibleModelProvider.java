package com.harnessagent.model;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.security.persistence.SecretStore;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.stereotype.Component;

@Component
public class OpenAICompatibleModelProvider implements ModelProvider {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final HarnessAgentProperties properties;
    private final SecretStore secretStore;

    public OpenAICompatibleModelProvider(HarnessAgentProperties properties, SecretStore secretStore) {
        this.properties = properties;
        this.secretStore = secretStore;
    }

    @Override
    public String id() {
        return "openai-compatible";
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
            throw new IllegalStateException("OpenAI-compatible API key is not configured");
        }
        String requestedModelName = request == null ? null : request.modelName();
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(firstNonBlank(requestedModelName, definition.getModelName(), DEFAULT_MODEL));
        if (definition.getBaseUrl() != null && !definition.getBaseUrl().isBlank()) {
            builder.baseUrl(definition.getBaseUrl().trim());
        }
        if (definition.getEndpointPath() != null && !definition.getEndpointPath().isBlank()) {
            builder.endpointPath(definition.getEndpointPath().trim());
        }
        return builder.build();
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
