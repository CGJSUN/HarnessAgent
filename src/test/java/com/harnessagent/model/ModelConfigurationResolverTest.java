package com.harnessagent.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

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

    @Test
    void defaultConfigurationDefinesDeepSeekWithoutChangingDefaultProvider() throws IOException {
        HarnessAgentProperties properties = loadApplicationProperties();

        assertThat(properties.getDefaultProvider()).isEqualTo("echo");
        assertThat(properties.getAgents().get("personal-assistant").getModelProvider()).isEqualTo("echo");
        HarnessAgentProperties.ModelProviderDefinition deepseek =
                properties.getModelProviders().get("deepseek");
        assertThat(deepseek).isNotNull();
        assertThat(deepseek.getType()).isEqualTo("openai-compatible");
        assertThat(deepseek.getModelName()).isEqualTo("deepseek-v4-flash");
        assertThat(deepseek.getApiKeyRef()).isEqualTo("env:DEEPSEEK_API_KEY");
        assertThat(deepseek.getBaseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(deepseek.getEndpointPath()).isEqualTo("/chat/completions");
    }

    @Test
    void resolvesDeepSeekAliasWithAgentModelOverrideAndFallback() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setModelProvider("deepseek");
        agent.setModelName("deepseek-v4-pro");
        agent.setFallbackProviders(List.of("echo"));
        properties.getAgents().put("personal-assistant", agent);
        HarnessAgentProperties.ModelProviderDefinition deepseek =
                new HarnessAgentProperties.ModelProviderDefinition();
        deepseek.setType("openai-compatible");
        deepseek.setModelName("deepseek-v4-flash");
        deepseek.setApiKeyRef("env:DEEPSEEK_API_KEY");
        deepseek.setBaseUrl("https://api.deepseek.com");
        deepseek.setEndpointPath("/chat/completions");
        properties.getModelProviders().put("deepseek", deepseek);

        ModelSelection selection = new ModelConfigurationResolver(properties, new ProductionRuntimeProperties())
                .resolve("personal-assistant");

        assertThat(selection.providerId()).isEqualTo("deepseek");
        assertThat(selection.providerType()).isEqualTo("openai-compatible");
        assertThat(selection.modelName()).isEqualTo("deepseek-v4-pro");
        assertThat(selection.apiKeyRef()).isEqualTo("env:DEEPSEEK_API_KEY");
        assertThat(selection.fallbackProviders()).containsExactly("echo");

        ModelSelection fallback = new ModelConfigurationResolver(properties, new ProductionRuntimeProperties())
                .resolveFallback("personal-assistant", "deepseek");
        assertThat(fallback.providerId()).isEqualTo("deepseek");
        assertThat(fallback.providerType()).isEqualTo("openai-compatible");
        assertThat(fallback.modelName()).isEqualTo("deepseek-v4-flash");
        assertThat(fallback.apiKeyRef()).isEqualTo("env:DEEPSEEK_API_KEY");
        assertThat(fallback.fallbackProviders()).isEmpty();
    }

    private static HarnessAgentProperties loadApplicationProperties() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("application.yml", new ClassPathResource("application.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        ConfigurationPropertySources.attach(environment);
        return Binder.get(environment)
                .bind("harness-agent", HarnessAgentProperties.class)
                .orElseThrow(() -> new IllegalStateException("harness-agent config did not bind"));
    }
}
