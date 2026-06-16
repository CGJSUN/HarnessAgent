package com.harnessagent.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SafeLogFieldsTest {

    @Test
    void digestsAreStableAndDoNotExposeOriginalValue() {
        String first = SafeLogFields.user("user-a@example.com");
        String second = SafeLogFields.user(" user-a@example.com ");

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("sha256:");
        assertThat(first).doesNotContain("user-a");
    }

    @Test
    void blankIdentifiersUseEmptyMarker() {
        assertThat(SafeLogFields.session(null)).isEqualTo("empty");
        assertThat(SafeLogFields.idempotency(" ")).isEqualTo("empty");
    }

    @Test
    void reasonCodesAreNormalizedForLogging() {
        assertThat(SafeLogFields.reasonCode(" Prompt Injection Blocked "))
                .isEqualTo("prompt_injection_blocked");
        assertThat(SafeLogFields.reasonCode(null)).isEqualTo("unspecified");
    }
}
