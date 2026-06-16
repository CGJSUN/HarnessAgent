package com.harnessagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import com.harnessagent.production.budget.BudgetLimit;
import org.junit.jupiter.api.Test;

class ModelProviderRegistryTest {

    @Test
    void resolvesKnownProvider() {
        EchoModelProvider echo = new EchoModelProvider();
        ModelProviderRegistry registry = new ModelProviderRegistry(List.of(echo));

        assertThat(registry.requireProvider("echo")).isSameAs(echo);
    }

    @Test
    void rejectsUnknownProvider() {
        ModelProviderRegistry registry = new ModelProviderRegistry(List.of(new EchoModelProvider()));

        assertThatThrownBy(() -> registry.requireProvider("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown model provider");
    }

    @Test
    void resolvesConfiguredProviderByType() {
        EchoModelProvider echo = new EchoModelProvider();
        ModelProviderRegistry registry = new ModelProviderRegistry(List.of(echo));

        assertThat(registry.requireProvider(new ModelSelection(
                        "personal-echo",
                        "echo",
                        "echo-local",
                        null,
                        new BudgetLimit(1, 10),
                        List.of())))
                .isSameAs(echo);
    }
}
