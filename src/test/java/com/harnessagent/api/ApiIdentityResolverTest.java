package com.harnessagent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harnessagent.security.domain.IdentityProviderType;
import com.harnessagent.security.domain.OwnerPrincipal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiIdentityResolverTest {

    private final ApiIdentityResolver resolver = new ApiIdentityResolver();

    @Test
    void usesTrustedOwnerHeaderWhenPresent() {
        OwnerPrincipal principal = resolver.resolve(
                Map.of("X-Owner-Id", "owner-a", "X-Identity-Provider", "lark"),
                "owner-a");

        assertThat(principal.ownerId()).isEqualTo("owner-a");
        assertThat(principal.providerType()).isEqualTo(IdentityProviderType.LARK);
    }

    @Test
    void rejectsRequestOwnerThatConflictsWithTrustedHeader() {
        assertThatThrownBy(() -> resolver.resolve(
                        Map.of("X-Owner-Id", "owner-a"),
                        "owner-b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void fallsBackToRequestOwnerForLocalDevelopment() {
        OwnerPrincipal principal = resolver.resolve(Map.of(), "owner-a");

        assertThat(principal.providerType()).isEqualTo(IdentityProviderType.INTERNAL);
        assertThat(principal.ownerId()).isEqualTo("owner-a");
    }

    @Test
    void defaultsToPersonalIdentityWhenLocalRequestOmitsOwner() {
        OwnerPrincipal principal = resolver.resolve(Map.of(), null);

        assertThat(principal.ownerId()).isEqualTo("personal-user");
    }

    @Test
    void trustedOwnerIdentityRequiresHeader() {
        assertThatThrownBy(() -> resolver.resolveTrustedOwner(Map.of(), "owner-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authenticated ownerId is required");
    }

    @Test
    void trustedIdentityRequiresOwnerHeader() {
        assertThatThrownBy(() -> resolver.resolveTrusted(Map.of(), "owner-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authenticated ownerId is required");
    }

    @Test
    void trustedAgentRequiresHeaderMatch() {
        assertThat(resolver.resolveTrustedAgentId(Map.of("X-Agent-Id", "agent-a"), "agent-a"))
                .isEqualTo("agent-a");

        assertThatThrownBy(() -> resolver.resolveTrustedAgentId(Map.of("X-Agent-Id", "agent-a"), "agent-b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agentId");
    }
}
