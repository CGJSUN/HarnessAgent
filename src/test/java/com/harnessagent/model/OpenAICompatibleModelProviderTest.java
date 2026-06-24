package com.harnessagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.security.persistence.SecretStore;
import io.agentscope.core.formatter.openai.DeepSeekFormatter;
import io.agentscope.core.model.GenerateOptions;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAICompatibleModelProviderTest {

    @Test
    void createsModelFromConfiguredProviderAliasAndRequestScopedApiKeyReference() {
        HarnessAgentProperties properties = properties();
        AtomicReference<String> resolvedReference = new AtomicReference<>();
        SecretStore secretStore = reference -> {
            resolvedReference.set(reference);
            return Optional.of("sk-personal");
        };

        var model = new OpenAICompatibleModelProvider(properties, secretStore)
                .createModel(new ModelProviderRequest(
                        "personal-openai",
                        "gpt-4.1-mini",
                        "env:PERSONAL_OPENAI_KEY"));

        assertThat(resolvedReference).hasValue("env:PERSONAL_OPENAI_KEY");
        assertThat(model.getModelName()).isEqualTo("gpt-4.1-mini");
    }

    @Test
    void createsDeepSeekModelFromConfiguredOpenAiCompatibleAlias() throws Exception {
        HarnessAgentProperties properties = properties();
        AtomicReference<String> resolvedReference = new AtomicReference<>();
        SecretStore secretStore = reference -> {
            resolvedReference.set(reference);
            return Optional.of("sk-deepseek-test");
        };

        var model = new OpenAICompatibleModelProvider(properties, secretStore)
                .createModel(new ModelProviderRequest("deepseek", null, null));

        GenerateOptions options = configuredOptions(model);
        assertThat(resolvedReference).hasValue("env:DEEPSEEK_API_KEY");
        assertThat(model.getModelName()).isEqualTo("deepseek-v4-flash");
        assertThat(options.getApiKey()).isEqualTo("sk-deepseek-test");
        assertThat(options.getBaseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(options.getEndpointPath()).isEqualTo("/chat/completions");
        assertThat(options.getModelName()).isEqualTo("deepseek-v4-flash");
        assertThat(configuredFormatter(model)).isInstanceOf(DeepSeekFormatter.class);
    }

    @Test
    void rejectsMissingApiKeyReference() {
        assertThatThrownBy(() -> new OpenAICompatibleModelProvider(properties(), reference -> Optional.empty())
                        .createModel(new ModelProviderRequest(
                                "personal-openai",
                                "gpt-4.1-mini",
                                "env:MISSING_KEY")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OpenAI-compatible API key is not configured");
    }

    @Test
    void rejectsMissingDeepSeekApiKeyReference() {
        assertThatThrownBy(() -> new OpenAICompatibleModelProvider(properties(), reference -> Optional.empty())
                        .createModel(new ModelProviderRequest(
                                "deepseek",
                                "deepseek-v4-pro",
                                null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OpenAI-compatible API key is not configured");
    }

    private static HarnessAgentProperties properties() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.ModelProviderDefinition provider = new HarnessAgentProperties.ModelProviderDefinition();
        provider.setType("openai-compatible");
        provider.setModelName("gpt-4o-mini");
        provider.setApiKeyRef("env:OPENAI_API_KEY");
        provider.setBaseUrl("https://api.openai.example/v1");
        properties.getModelProviders().put("personal-openai", provider);
        HarnessAgentProperties.ModelProviderDefinition deepseek = new HarnessAgentProperties.ModelProviderDefinition();
        deepseek.setType("openai-compatible");
        deepseek.setModelName("deepseek-v4-flash");
        deepseek.setApiKeyRef("env:DEEPSEEK_API_KEY");
        deepseek.setBaseUrl("https://api.deepseek.com");
        deepseek.setEndpointPath("/chat/completions");
        properties.getModelProviders().put("deepseek", deepseek);
        return properties;
    }

    private static GenerateOptions configuredOptions(Object model) throws Exception {
        Field field = model.getClass().getDeclaredField("configuredOptions");
        field.setAccessible(true);
        return (GenerateOptions) field.get(model);
    }

    private static Object configuredFormatter(Object model) throws Exception {
        Field field = model.getClass().getDeclaredField("formatter");
        field.setAccessible(true);
        return field.get(model);
    }
}
