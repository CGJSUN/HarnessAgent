package com.harnessagent.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelConfigurationResolverTest {

    @Test
    void resolvesAgentLevelProviderModelSecretAndBudget() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        properties.setDefaultProvider("echo");
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setModelProvider("dashscope");
        agent.setModelName("qwen-max");
        agent.setModelApiKeyRef("env:PERSONAL_DASHSCOPE_KEY");
        agent.setFallbackProviders(List.of("echo"));
        agent.getBudget().setRequestLimit(2L);
        agent.getBudget().setTokenLimit(20L);
        properties.getAgents().put("personal-assistant", agent);
        HarnessAgentProperties.ModelProviderDefinition echo = new HarnessAgentProperties.ModelProviderDefinition();
        echo.setModelName("echo-local");
        HarnessAgentProperties.ModelProviderDefinition dashscope = new HarnessAgentProperties.ModelProviderDefinition();
        dashscope.setModelName("qwen-plus");
        dashscope.setApiKeyEnv("DASHSCOPE_API_KEY");
        properties.getModelProviders().put("echo", echo);
        properties.getModelProviders().put("dashscope", dashscope);
        ProductionRuntimeProperties runtime = new ProductionRuntimeProperties();
        runtime.getBudget().setRequestLimit(100);
        runtime.getBudget().setTokenLimit(1000);

        ModelSelection selection = new ModelConfigurationResolver(properties, runtime)
                .resolve("personal-assistant");

        assertThat(selection.providerId()).isEqualTo("dashscope");
        assertThat(selection.providerType()).isEqualTo("dashscope");
        assertThat(selection.modelName()).isEqualTo("qwen-max");
        assertThat(selection.apiKeyRef()).isEqualTo("env:PERSONAL_DASHSCOPE_KEY");
        assertThat(selection.budgetLimit().requestLimit()).isEqualTo(2);
        assertThat(selection.budgetLimit().tokenLimit()).isEqualTo(20);
        assertThat(selection.fallbackProviders()).containsExactly("echo");
    }

    @Test
    void resolvesFallbackProviderWithProviderDefaultModelAndSecret() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setModelProvider("dashscope");
        agent.setModelName("qwen-max");
        agent.setModelApiKeyRef("env:PRIMARY_KEY");
        properties.getAgents().put("personal-assistant", agent);
        HarnessAgentProperties.ModelProviderDefinition echo = new HarnessAgentProperties.ModelProviderDefinition();
        echo.setModelName("echo-local");
        properties.getModelProviders().put("echo", echo);

        ModelSelection selection = new ModelConfigurationResolver(properties, new ProductionRuntimeProperties())
                .resolveFallback("personal-assistant", "echo");

        assertThat(selection.providerId()).isEqualTo("echo");
        assertThat(selection.modelName()).isEqualTo("echo-local");
        assertThat(selection.apiKeyRef()).isNull();
        assertThat(selection.fallbackProviders()).isEmpty();
    }

    @Test
    void resolvesConfiguredProviderTypeForOpenAiCompatibleAliases() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setModelProvider("personal-openai");
        properties.getAgents().put("personal-assistant", agent);
        HarnessAgentProperties.ModelProviderDefinition provider = new HarnessAgentProperties.ModelProviderDefinition();
        provider.setType("openai-compatible");
        provider.setModelName("gpt-4o-mini");
        provider.setApiKeyRef("env:OPENAI_API_KEY");
        properties.getModelProviders().put("personal-openai", provider);

        ModelSelection selection = new ModelConfigurationResolver(properties, new ProductionRuntimeProperties())
                .resolve("personal-assistant");

        assertThat(selection.providerId()).isEqualTo("personal-openai");
        assertThat(selection.providerType()).isEqualTo("openai-compatible");
        assertThat(selection.modelName()).isEqualTo("gpt-4o-mini");
        assertThat(selection.apiKeyRef()).isEqualTo("env:OPENAI_API_KEY");
    }
}
