package com.harnessagent.rag.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemoryRagProviderConfiguration {

    @Bean
    public LocalMemoryRagProvider localMemoryRagProvider(KnowledgeService knowledgeService) {
        return new LocalMemoryRagProvider(knowledgeService);
    }

    @Bean
    public MemoryRagProviderRegistry memoryRagProviderRegistry(LocalMemoryRagProvider localProvider) {
        return MemoryRagProviderRegistry.withLocalProvider(localProvider);
    }
}
