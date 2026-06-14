package com.harnessagent.model;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ModelProviderRegistry {

    private final Map<String, ModelProvider> providers;

    public ModelProviderRegistry(Iterable<ModelProvider> providers) {
        this.providers = toMap(providers);
    }

    public ModelProvider requireProvider(String providerId) {
        ModelProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown model provider: " + providerId);
        }
        return provider;
    }

    private static Map<String, ModelProvider> toMap(Iterable<ModelProvider> providers) {
        java.util.List<ModelProvider> providerList = new java.util.ArrayList<>();
        providers.forEach(providerList::add);
        return providerList.stream()
                .collect(Collectors.toUnmodifiableMap(ModelProvider::id, Function.identity()));
    }
}
