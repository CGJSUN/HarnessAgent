package com.harnessagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.security.persistence.SecretStore;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DashScopeModelProviderTest {

    @Test
    void resolvesRequestScopedApiKeyReferenceThroughSecretStore() {
        HarnessAgentProperties properties = properties();
        AtomicReference<String> resolvedReference = new AtomicReference<>();
        SecretStore secretStore = reference -> {
            resolvedReference.set(reference);
            return Optional.of("sk-personal");
        };

        var model = new DashScopeModelProvider(properties, secretStore)
                .createModel(new ModelProviderRequest("dashscope", "qwen-max", "env:PERSONAL_DASHSCOPE_KEY"));

        assertThat(resolvedReference).hasValue("env:PERSONAL_DASHSCOPE_KEY");
        assertThat(model.getModelName()).isEqualTo("qwen-max");
    }

    @Test
    void rejectsMissingRequestScopedApiKeyReference() {
        assertThatThrownBy(() -> new DashScopeModelProvider(properties(), reference -> Optional.empty())
                        .createModel(new ModelProviderRequest("dashscope", "qwen-max", "env:MISSING_KEY")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DashScope API key is not configured");
    }

    private static HarnessAgentProperties properties() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.ModelProviderDefinition dashscope = new HarnessAgentProperties.ModelProviderDefinition();
        dashscope.setModelName("qwen-plus");
        properties.getModelProviders().put("dashscope", dashscope);
        return properties;
    }
}
