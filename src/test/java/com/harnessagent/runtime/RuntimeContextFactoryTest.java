package com.harnessagent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RuntimeContextFactoryTest {

    private final RuntimeContextFactory factory = new RuntimeContextFactory();

    @Test
    void createsRuntimeKeysFromOwnerAgentAndSession() {
        RuntimeContextScope context = factory.createPersonal("owner-a", "agent-a", "session-a");

        assertThat(context.ownerId()).isEqualTo("owner-a");
        assertThat(context.ownerScope()).isEqualTo(new OwnerScope("owner-a", "agent-a", "session-a"));
        assertThat(context.runtimeUserId()).isEqualTo("owner:owner-a");
        assertThat(context.runtimeSessionId()).isEqualTo("agent-a:session-a");
    }

    @Test
    void defaultsBlankOwnerScopeAndUserToPersonalOwnerContext() {
        RuntimeContextScope context = factory.createPersonal(null, "personal-agent", "session-a");

        assertThat(context.ownerId()).isEqualTo("personal-user");
        assertThat(context.runtimeUserId()).isEqualTo("owner:personal-user");
        assertThat(context.runtimeSessionId()).isEqualTo("personal-agent:session-a");
    }

    @Test
    void usesRequestUserAsPersonalOwnerWhenOwnerScopeIsBlank() {
        RuntimeContextScope context = factory.createFromOwnerScope("", "alice", "personal-agent", "session-a");

        assertThat(context.ownerId()).isEqualTo("alice");
        assertThat(context.ownerScopeId()).isEqualTo("personal");
        assertThat(context.runtimeUserId()).isEqualTo("owner:alice");
    }

    @Test
    void rejectsBlankValues() {
        assertThatThrownBy(() -> factory.createFromOwnerScope("owner-scope-a", " ", "agent-a", "session-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownerId is required");
    }

    @Test
    void stillRejectsBlankAgentForPersonalContext() {
        assertThatThrownBy(() -> factory.createPersonal(null, " ", "session-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId is required");
    }

    @Test
    void rejectsSeparatorInValues() {
        assertThatThrownBy(() -> factory.createFromOwnerScope("owner-scope:a", "user-a", "agent-a", "session-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain ':'");
    }
}
