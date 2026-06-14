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
    void rejectsBlankValues() {
        assertThatThrownBy(() -> factory.create("tenant-a", " ", "agent-a", "session-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId is required");
    }

    @Test
    void rejectsSeparatorInValues() {
        assertThatThrownBy(() -> factory.create("tenant:a", "user-a", "agent-a", "session-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain ':'");
    }
}
