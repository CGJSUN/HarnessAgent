package com.harnessagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.security.persistence.SecretStore;
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
    void rejectsMissingApiKeyReference() {
        assertThatThrownBy(() -> new OpenAICompatibleModelProvider(properties(), reference -> Optional.empty())
                        .createModel(new ModelProviderRequest(
                                "personal-openai",
                                "gpt-4.1-mini",
                                "env:MISSING_KEY")))
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
        return properties;
    }
}
