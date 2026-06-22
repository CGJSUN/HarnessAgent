package com.harnessagent.rag.application;

import com.harnessagent.rag.domain.MemoryRagProviderDescriptor;
import com.harnessagent.rag.domain.MemoryRagProviderType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MemoryRagProviderRegistry {

    private final Map<String, MemoryRagProvider> providers;

    public MemoryRagProviderRegistry(List<MemoryRagProvider> providers) {
        Map<String, MemoryRagProvider> indexed = new LinkedHashMap<>();
        providers.forEach(provider -> indexed.put(normalize(provider.id()), provider));
        this.providers = Map.copyOf(indexed);
    }

    public static MemoryRagProviderRegistry withLocalProvider(LocalMemoryRagProvider localProvider) {
        return new MemoryRagProviderRegistry(List.of(
                localProvider,
                placeholder("bailian", MemoryRagProviderType.BAILIAN),
                placeholder("mem0", MemoryRagProviderType.MEM0),
                placeholder("reme", MemoryRagProviderType.REME),
                placeholder("dify", MemoryRagProviderType.DIFY),
                placeholder("haystack", MemoryRagProviderType.HAYSTACK),
                placeholder("ragflow", MemoryRagProviderType.RAGFLOW)));
    }

    public MemoryRagProvider provider(String id) {
        MemoryRagProvider provider = providers.get(normalize(id));
        if (provider == null) {
            throw new IllegalArgumentException("Unknown Memory/RAG provider: " + id);
        }
        return provider;
    }

    public List<MemoryRagProviderDescriptor> descriptors() {
        return providers.values().stream()
                .map(MemoryRagProvider::descriptor)
                .toList();
    }

    private static MemoryRagProvider placeholder(String id, MemoryRagProviderType type) {
        return new MemoryRagProvider() {
            @Override
            public MemoryRagProviderDescriptor descriptor() {
                return new MemoryRagProviderDescriptor(
                        id,
                        type,
                        false,
                        type.name() + " adapter point is available but not configured.");
            }
        };
    }

    private static String normalize(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("provider id is required");
        }
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
