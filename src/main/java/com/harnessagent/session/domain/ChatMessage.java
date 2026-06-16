package com.harnessagent.session.domain;

import com.harnessagent.chat.domain.ContentBlock;
import com.harnessagent.chat.domain.ContentBlockType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessage(String id, MessageRole role, String content, List<ContentBlock> contentBlocks, Instant createdAt) {

    public ChatMessage(String id, MessageRole role, String content, Instant createdAt) {
        this(id, role, content, List.of(ContentBlock.text(content == null ? "" : content)), createdAt);
    }

    public ChatMessage(String id, MessageRole role, List<ContentBlock> contentBlocks, Instant createdAt) {
        this(id, role, textContent(contentBlocks), contentBlocks, createdAt);
    }

    public ChatMessage {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("message id is required");
        }
        if (role == null) {
            throw new IllegalArgumentException("message role is required");
        }
        contentBlocks = normalize(content, contentBlocks);
        content = textContent(contentBlocks);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(UUID.randomUUID().toString(), MessageRole.USER, content, Instant.now());
    }

    public static ChatMessage user(List<ContentBlock> contentBlocks) {
        return new ChatMessage(UUID.randomUUID().toString(), MessageRole.USER, contentBlocks, Instant.now());
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(
                UUID.randomUUID().toString(), MessageRole.ASSISTANT, content, Instant.now());
    }

    public static ChatMessage assistant(List<ContentBlock> contentBlocks) {
        return new ChatMessage(UUID.randomUUID().toString(), MessageRole.ASSISTANT, contentBlocks, Instant.now());
    }

    private static List<ContentBlock> normalize(String content, List<ContentBlock> contentBlocks) {
        if (contentBlocks == null || contentBlocks.isEmpty()) {
            return List.of(ContentBlock.text(content == null ? "" : content));
        }
        return List.copyOf(contentBlocks);
    }

    private static String textContent(List<ContentBlock> contentBlocks) {
        if (contentBlocks == null || contentBlocks.isEmpty()) {
            return "";
        }
        return contentBlocks.stream()
                .filter(block -> block.type() == ContentBlockType.TEXT)
                .map(ContentBlock::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
