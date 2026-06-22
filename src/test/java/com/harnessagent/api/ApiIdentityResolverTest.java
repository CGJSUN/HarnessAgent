package com.harnessagent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harnessagent.security.domain.IdentityProviderType;
import com.harnessagent.security.domain.SecurityPrincipal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiIdentityResolverTest {

    private final ApiIdentityResolver resolver = new ApiIdentityResolver();

    @Test
    void usesTrustedHeadersWhenPresent() {
        SecurityPrincipal principal = resolver.resolve(
                Map.of(
                        "X-Tenant-Id", "tenant-a",
                        "X-User-Id", "user-a",
                        "X-Identity-Provider", "lark",
                        "X-Roles", "employee,auditor",
                        "X-Departments", "finance"),
                "tenant-a",
                "user-a",
                Set.of("forged-role"),
                Set.of("forged-dept"));

        assertThat(principal.providerType()).isEqualTo(IdentityProviderType.LARK);
        assertThat(principal.roles()).containsExactlyInAnyOrder("employee", "auditor");
        assertThat(principal.departments()).containsExactly("finance");
    }

    @Test
    void rejectsRequestBodyIdentityThatConflictsWithTrustedHeaders() {
        assertThatThrownBy(() -> resolver.resolve(
                        Map.of("X-Tenant-Id", "tenant-a", "X-User-Id", "user-a"),
                        "tenant-b",
                        "user-a",
                        Set.of(),
                        Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void fallsBackToRequestBodyForLocalDevelopment() {
        SecurityPrincipal principal = resolver.resolve(
                Map.of(),
                "tenant-a",
                "user-a",
                Set.of("employee"),
                Set.of("finance"));

        assertThat(principal.providerType()).isEqualTo(IdentityProviderType.INTERNAL);
        assertThat(principal.roles()).containsExactly("employee");
    }

    @Test
    void defaultsToPersonalIdentityWhenLocalRequestOmitsEnterpriseIdentity() {
        SecurityPrincipal principal = resolver.resolve(
                Map.of(),
                null,
                null,
                Set.of(),
                Set.of());

        assertThat(principal.tenantId()).isEqualTo("personal");
        assertThat(principal.userId()).isEqualTo("personal-user");
        assertThat(principal.roles()).isEmpty();
        assertThat(principal.departments()).isEmpty();
    }

    @Test
    void mapsLocalRequestUserToPersonalOwnerWhenTenantIsBlank() {
        SecurityPrincipal principal = resolver.resolve(
                Map.of(),
                "",
                "alice",
                Set.of("local-user"),
                Set.of());

        assertThat(principal.tenantId()).isEqualTo("personal");
        assertThat(principal.userId()).isEqualTo("alice");
        assertThat(principal.roles()).containsExactly("local-user");
    }

    @Test
    void trustedIdentityRequiresHeaders() {
        assertThatThrownBy(() -> resolver.resolveTrusted(Map.of(), "tenant-a", "user-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authenticated tenantId is required");
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
