package com.harnessagent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RuntimeContextFactoryTest {

    private final RuntimeContextFactory factory = new RuntimeContextFactory();

    @Test
    void createsRuntimeKeysFromTenantUserAgentAndSession() {
        RuntimeContextScope context = factory.create("tenant-a", "user-a", "agent-a", "session-a");

        assertThat(context.runtimeUserId()).isEqualTo("tenant-a:user-a");
        assertThat(context.runtimeSessionId()).isEqualTo("agent-a:session-a");
    }

    @Test
    void defaultsBlankTenantAndUserToPersonalOwnerContext() {
        RuntimeContextScope context = factory.create(null, " ", "personal-agent", "session-a");

        assertThat(context.tenantId()).isEqualTo("personal");
        assertThat(context.userId()).isEqualTo("personal-user");
        assertThat(context.runtimeUserId()).isEqualTo("personal:personal-user");
        assertThat(context.runtimeSessionId()).isEqualTo("personal-agent:session-a");
    }

    @Test
    void usesRequestUserAsPersonalOwnerWhenTenantIsBlank() {
        RuntimeContextScope context = factory.create("", "alice", "personal-agent", "session-a");

        assertThat(context.tenantId()).isEqualTo("personal");
        assertThat(context.userId()).isEqualTo("alice");
        assertThat(context.runtimeUserId()).isEqualTo("personal:alice");
    }

    @Test
    void rejectsBlankValues() {
        assertThatThrownBy(() -> factory.create("tenant-a", " ", "agent-a", "session-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId is required");
    }

    @Test
    void stillRejectsBlankAgentForPersonalContext() {
        assertThatThrownBy(() -> factory.create(null, null, " ", "session-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId is required");
    }

    @Test
    void rejectsSeparatorInValues() {
        assertThatThrownBy(() -> factory.create("tenant:a", "user-a", "agent-a", "session-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain ':'");
    }
}
