package com.harnessagent.workspace.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.chat.domain.ContentBlock;
import com.harnessagent.chat.domain.ContentBlockType;
import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.domain.MessageRole;
import com.harnessagent.workspace.domain.ContextCompactionSummary;
import com.harnessagent.workspace.domain.PersonalWorkspaceLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ContextCompactionService {

    private static final String WORKSPACE_URI_PREFIX = "workspace://";

    private final PersonalWorkspaceService workspaceService;
    private final HarnessAgentProperties properties;
    private final ObjectMapper objectMapper;

    public ContextCompactionService(
            PersonalWorkspaceService workspaceService,
            HarnessAgentProperties properties) {
        this(workspaceService, properties, new ObjectMapper().findAndRegisterModules());
    }

    ContextCompactionService(
            PersonalWorkspaceService workspaceService,
            HarnessAgentProperties properties,
            ObjectMapper objectMapper) {
        this.workspaceService = workspaceService;
        this.properties = properties == null ? new HarnessAgentProperties() : properties;
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
    }

    public static ContextCompactionService disabled() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setCompaction(false);
        properties.getAgents().put("__disabled__", agent);
        return new ContextCompactionService(new PersonalWorkspaceService(properties), properties) {
            @Override
            public List<ChatMessage> compactIfNeeded(RuntimeContextScope context, List<ChatMessage> messages) {
                return messages == null ? List.of() : List.copyOf(messages);
            }
        };
    }

    public List<ChatMessage> compactIfNeeded(RuntimeContextScope context, List<ChatMessage> messages) {
        List<ChatMessage> safeMessages = messages == null ? List.of() : List.copyOf(messages);
        if (safeMessages.size() <= threshold(context) || !enabled(context)) {
            return safeMessages;
        }
        if (safeMessages.size() < 2) {
            return safeMessages;
        }
        ChatMessage retained = safeMessages.get(safeMessages.size() - 1);
        List<ChatMessage> compacted = safeMessages.subList(0, safeMessages.size() - 1);
        ContextCompactionSummary summary = summarize(context, compacted, List.of(retained));
        write(context, summary);
        ChatMessage summaryMessage = new ChatMessage(
                "compaction-" + summary.id(),
                MessageRole.SYSTEM,
                List.of(
                        ContentBlock.text(summary.asPrompt()),
                        ContentBlock.file(summary.uri(), "application/json", summary.id() + ".json")),
                summary.createdAt());
        return List.of(summaryMessage, retained);
    }

    public ContextCompactionSummary summarize(
            RuntimeContextScope context,
            List<ChatMessage> sourceMessages,
            List<ChatMessage> retainedMessages) {
        List<ChatMessage> source = sourceMessages == null ? List.of() : List.copyOf(sourceMessages);
        List<ChatMessage> retained = retainedMessages == null ? List.of() : List.copyOf(retainedMessages);
        String id = sha256(context.ownerId()
                + ":" + context.agentId()
                + ":" + context.sessionId()
                + ":" + source.stream().map(ChatMessage::id).reduce("", String::concat))
                .substring(0, 16);
        String uri = WORKSPACE_URI_PREFIX
                + relativeCompactionPath(context, id).toString().replace('\\', '/');
        return new ContextCompactionSummary(
                id,
                context.ownerId(),
                context.agentId(),
                context.sessionId(),
                uri,
                firstUserMessage(source),
                lastAssistantMessage(source),
                collectLines(source, List.of("finding", "发现", "evidence", "result", "结果", "trace")),
                collectLines(source, List.of("decision", "decide", "决定", "confirmed", "确认")),
                collectFileReferences(source),
                nextSteps(source, retained),
                source.stream().map(ChatMessage::id).toList(),
                retained.stream().map(ChatMessage::id).toList(),
                Instant.now());
    }

    private boolean enabled(RuntimeContextScope context) {
        HarnessAgentProperties.AgentDefinition agent = properties.getAgents().get(context.agentId());
        return agent == null || agent.isCompaction();
    }

    private int threshold(RuntimeContextScope context) {
        HarnessAgentProperties.AgentDefinition agent = properties.getAgents().get(context.agentId());
        return agent == null ? 24 : Math.max(2, agent.getCompactionMessageThreshold());
    }

    private void write(RuntimeContextScope context, ContextCompactionSummary summary) {
        PersonalWorkspaceLayout layout = workspaceService.initialize(context);
        Path file = layout.root().resolve(relativeCompactionPath(context, summary.id())).normalize();
        if (!file.startsWith(layout.root())) {
            throw new IllegalStateException("Context compaction path escapes workspace.");
        }
        try {
            Files.createDirectories(file.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), summary);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to write context compaction summary.", ex);
        }
    }

    private static Path relativeCompactionPath(RuntimeContextScope context, String id) {
        return Path.of("sessions", "session-" + sha256(context.sessionId()).substring(0, 16), "compactions",
                "context-" + id + ".json");
    }

    private static String firstUserMessage(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message.role() == MessageRole.USER)
                .map(ChatMessage::content)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .map(ContextCompactionService::shorten)
                .orElse("No explicit goal captured.");
    }

    private static String lastAssistantMessage(List<ChatMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatMessage message = messages.get(index);
            if (message.role() == MessageRole.ASSISTANT && message.content() != null && !message.content().isBlank()) {
                return shorten(message.content());
            }
        }
        return "No current state captured.";
    }

    private static List<String> collectLines(List<ChatMessage> messages, List<String> keywords) {
        List<String> values = new ArrayList<>();
        for (ChatMessage message : messages) {
            for (String line : splitLines(message.content())) {
                String normalized = line.toLowerCase(Locale.ROOT);
                if (keywords.stream().anyMatch(normalized::contains)) {
                    values.add(shorten(line));
                }
            }
        }
        return values.stream().distinct().limit(8).toList();
    }

    private static List<String> collectFileReferences(List<ChatMessage> messages) {
        Set<String> references = new LinkedHashSet<>();
        for (ChatMessage message : messages) {
            for (ContentBlock block : message.contentBlocks()) {
                if (isReference(block.type()) && block.uri() != null && !block.uri().isBlank()) {
                    references.add(block.uri());
                }
            }
            for (String token : message.content().split("\\s+")) {
                if (token.startsWith(WORKSPACE_URI_PREFIX)) {
                    references.add(token.replaceAll("[,.;)]+$", ""));
                }
            }
        }
        return references.stream().limit(12).toList();
    }

    private static List<String> nextSteps(List<ChatMessage> source, List<ChatMessage> retained) {
        List<String> values = collectLines(source, List.of("next", "todo", "下一步", "计划", "plan"));
        if (!values.isEmpty()) {
            return values;
        }
        return retained.stream()
                .map(ChatMessage::content)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> "Continue with latest user request: " + shorten(value))
                .limit(1)
                .toList();
    }

    private static boolean isReference(ContentBlockType type) {
        return type == ContentBlockType.FILE
                || type == ContentBlockType.IMAGE
                || type == ContentBlockType.AUDIO
                || type == ContentBlockType.VIDEO;
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static String shorten(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 237) + "...";
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available.", ex);
        }
    }
}
