package com.harnessagent.chat.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentBlockTest {

    @Test
    void rejectsInvalidContentBlockCombinations() {
        assertThatThrownBy(() -> new ContentBlock(ContentBlockType.TEXT, null, null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
        assertThatThrownBy(() -> new ContentBlock(ContentBlockType.FILE, null, "", "text/plain", "note.txt", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uri");
        assertThatThrownBy(() -> new ContentBlock(ContentBlockType.THINKING, " ", null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thinking");
        assertThatThrownBy(() -> ContentBlock.toolResult(" ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolName");
    }
}
