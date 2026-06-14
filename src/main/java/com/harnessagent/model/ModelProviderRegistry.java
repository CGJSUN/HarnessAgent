package com.harnessagent.model;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ModelProviderRegistry {

    private final Map<String, ModelProvider> providers;

    public ModelProviderRegistry(List<ModelProvider> providers) {
        this.providers = toMap(providers);
    }

    public ModelProvider requireProvider(String providerId) {
        ModelProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown model provider: " + providerId);
        }
        return provider;
    }

    private static Map<String, ModelProvider> toMap(List<ModelProvider> providers) {
        return providers.stream()
                .collect(Collectors.toUnmodifiableMap(ModelProvider::id, Function.identity()));
    }
}
