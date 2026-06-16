package com.harnessagent.session.domain;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(String id, MessageRole role, String content, Instant createdAt) {

    public static ChatMessage user(String content) {
        return new ChatMessage(UUID.randomUUID().toString(), MessageRole.USER, content, Instant.now());
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(
                UUID.randomUUID().toString(), MessageRole.ASSISTANT, content, Instant.now());
    }
}
