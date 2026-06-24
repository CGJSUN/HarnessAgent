package com.harnessagent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.agent.runtime.AgentRuntimeEventType;
import com.harnessagent.api.controller.ApiExceptionHandler;
import com.harnessagent.api.controller.ChatController;
import com.harnessagent.api.controller.ConsoleController;
import com.harnessagent.api.controller.KnowledgeController;
import com.harnessagent.api.controller.OrchestrationController;
import com.harnessagent.api.controller.ReadinessController;
import com.harnessagent.api.controller.SessionController;
import com.harnessagent.api.controller.SkillController;
import com.harnessagent.api.controller.ToolController;
import com.harnessagent.api.request.ChatRequest;
import com.harnessagent.api.request.OrchestrationApiRequest;
import com.harnessagent.api.request.SkillValidationRequest;
import com.harnessagent.api.request.ToolExecutionApiRequest;
import com.harnessagent.api.response.ChatResponse;
import com.harnessagent.api.response.ErrorResponse;
import com.harnessagent.api.response.PersonalSkillResponse;
import com.harnessagent.api.response.StreamEventKind;
import com.harnessagent.api.response.StreamEventResponse;
import com.harnessagent.chat.domain.ChatCommand;
import com.harnessagent.chat.domain.ChatExecutionSummary;
import com.harnessagent.chat.domain.ChatResult;
import com.harnessagent.chat.domain.ContentBlock;
import com.harnessagent.chat.application.ChatService;
import com.harnessagent.orchestration.application.OrchestrationService;
import com.harnessagent.orchestration.domain.DelegationMode;
import com.harnessagent.orchestration.domain.FailureStrategy;
import com.harnessagent.orchestration.domain.OrchestrationRequest;
import com.harnessagent.orchestration.domain.OrchestrationResult;
import com.harnessagent.orchestration.domain.OrchestrationStatus;
import com.harnessagent.orchestration.domain.OrchestrationTrace;
import com.harnessagent.orchestration.domain.RouteDecision;
import com.harnessagent.rag.application.PersonalMemoryService;
import com.harnessagent.rag.domain.KnowledgeCitation;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.security.domain.IdentityProviderType;
import com.harnessagent.security.domain.OwnerPrincipal;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.domain.MessageRole;
import com.harnessagent.session.persistence.SessionStore;
import com.harnessagent.skill.domain.PersonalSkillStatus;
import com.harnessagent.skill.domain.SkillPermissionSet;
import com.harnessagent.skill.domain.SkillRepositoryType;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;

class ApiContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void chatEndpointMappingAndJsonShapeRemainStable() throws Exception {
        assertRootMapping(ChatController.class, "/api/chat");
        assertPostMapping(ChatController.class, "chat", new String[0]);
        assertPostMapping(
                ChatController.class,
                "stream",
                new String[] {"/stream"},
                new String[] {MediaType.TEXT_EVENT_STREAM_VALUE});

        KnowledgeCitation citation = new KnowledgeCitation("source-a", "Title", "v1", 2, "chunk-a");
        ChatResponse response = new ChatResponse(
                "assistant-1",
                "session-a",
                "answer",
                List.of(ContentBlock.text("answer")),
                new ChatExecutionSummary("completed", true, 1, null, "user-a", "agent-a:session-a"),
                "user-a",
                "agent-a:session-a",
                true,
                null,
                List.of(citation));
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder(
                        "messageId",
                        "sessionId",
                        "message",
                        "contentBlocks",
                        "executionSummary",
                        "runtimeUserId",
                        "runtimeSessionId",
                        "knowledgeBacked",
                        "noAnswerReason",
                        "citations");
        assertThat(json.get("messageId").asText()).isEqualTo("assistant-1");
        assertThat(json.get("sessionId").asText()).isEqualTo("session-a");
        assertThat(json.get("contentBlocks").get(0).get("type").asText()).isEqualTo("TEXT");
        assertThat(json.get("contentBlocks").get(0).get("text").asText()).isEqualTo("answer");
        assertThat(json.get("executionSummary").get("status").asText()).isEqualTo("completed");
        assertThat(json.get("executionSummary").get("citationCount").asInt()).isEqualTo(1);
        assertThat(json.get("citations").get(0).get("sourceId").asText()).isEqualTo("source-a");
        assertThat(json.get("citations").get(0).get("chunkIndex").asInt()).isEqualTo(2);
    }

    @Test
    void personalApiDtoJsonUsesOwnerContract() throws Exception {
        JsonNode chat = objectMapper.readTree(objectMapper.writeValueAsString(new ChatRequest(
                "owner-a",
                "personal-assistant",
                "session-a",
                "question",
                true,
                3)));
        assertThat(chat.fieldNames()).toIterable()
                .containsExactlyInAnyOrder(
                        "ownerId",
                        "agentId",
                        "sessionId",
                        "message",
                        "knowledgeEnabled",
                        "knowledgeLimit");

        JsonNode tool = objectMapper.readTree(objectMapper.writeValueAsString(new ToolExecutionApiRequest(
                "owner-a",
                "personal-assistant",
                "session-a",
                "tool-a",
                Map.of("path", "workspace://notes.md"),
                true,
                "idem-a")));
        assertThat(tool.fieldNames()).toIterable()
                .containsExactlyInAnyOrder(
                        "ownerId",
                        "agentId",
                        "sessionId",
                        "toolId",
                        "parameters",
                        "confirmed",
                        "idempotencyKey");

        JsonNode skillRequest = objectMapper.readTree(objectMapper.writeValueAsString(new SkillValidationRequest(
                "owner-a",
                "personal-assistant",
                "workspace://skills/summarize")));
        assertThat(skillRequest.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("ownerId", "agentId", "skillDirectory");

        JsonNode skillResponse = objectMapper.readTree(objectMapper.writeValueAsString(new PersonalSkillResponse(
                "skill-a",
                "owner-a",
                "summarize",
                "Summarize notes",
                "1.0.0",
                Set.of("summarize"),
                SkillRepositoryType.LOCAL,
                "workspace://skills/summarize",
                new SkillPermissionSet(Set.of(), Set.of(), false, false, false),
                Set.of(),
                Set.of("personal-assistant"),
                PersonalSkillStatus.ENABLED,
                Instant.parse("2026-06-15T08:00:00Z"))));
        assertThat(skillResponse.fieldNames()).toIterable()
                .containsExactlyInAnyOrder(
                        "id",
                        "ownerId",
                        "name",
                        "description",
                        "version",
                        "triggers",
                        "sourceType",
                        "source",
                        "permissions",
                        "resources",
                        "agentIds",
                        "status",
                        "updatedAt");
    }

    @Test
    void chatControllerPreservesRequestIdentityAndResponseContract() {
        ChatService chatService = mock(ChatService.class);
        ApiIdentityResolver identityResolver = mock(ApiIdentityResolver.class);
        when(identityResolver.resolve(any(), any()))
                .thenReturn(new OwnerPrincipal(
                        "personal",
                        "trusted-user",
                        IdentityProviderType.INTERNAL));
        when(chatService.chat(any()))
                .thenReturn(Mono.just(ChatResult.noAnswer("No accessible evidence.", "trusted-user", "session-a")));

        ChatController controller = new ChatController(chatService, identityResolver);
        ChatResponse response = controller.chat(
                Map.of(),
                new ChatRequest(
                        "body-user",
                        "personal-assistant",
                        "session-a",
                        "question",
                        true,
                        3));

        ArgumentCaptor<ChatCommand> command = ArgumentCaptor.forClass(ChatCommand.class);
        verify(chatService).chat(command.capture());
        assertThat(command.getValue().ownerScopeId()).isEqualTo("personal");
        assertThat(command.getValue().ownerId()).isEqualTo("trusted-user");
        assertThat(command.getValue().knowledgeEnabled()).isTrue();
        assertThat(command.getValue().knowledgeLimit()).isEqualTo(3);

        assertThat(response.message()).isEqualTo("No accessible evidence.");
        assertThat(response.messageId()).isNotBlank();
        assertThat(response.sessionId()).isEqualTo("session-a");
        assertThat(response.contentBlocks()).singleElement()
                .satisfies(block -> {
                    assertThat(block.type().name()).isEqualTo("TEXT");
                    assertThat(block.text()).isEqualTo("No accessible evidence.");
                });
        assertThat(response.executionSummary().status()).isEqualTo("knowledge_no_answer");
        assertThat(response.runtimeUserId()).isEqualTo("trusted-user");
        assertThat(response.runtimeSessionId()).isEqualTo("session-a");
        assertThat(response.knowledgeBacked()).isFalse();
        assertThat(response.noAnswerReason()).isEqualTo("No accessible evidence.");
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void knowledgeControllerRejectsMemoryRequestIdentityMismatch() {
        KnowledgeController controller = new KnowledgeController(
                mock(com.harnessagent.rag.application.KnowledgeService.class),
                mock(PersonalMemoryService.class),
                new ApiIdentityResolver());

        assertThatThrownBy(() -> controller.listMemories(
                        Map.of("X-Owner-Id", "owner-a"),
                        "owner-b",
                        "agent-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void knowledgeControllerRequiresTrustedIdentityForPersonalMemoryExport() {
        KnowledgeController controller = new KnowledgeController(
                mock(com.harnessagent.rag.application.KnowledgeService.class),
                mock(PersonalMemoryService.class),
                new ApiIdentityResolver());

        assertThatThrownBy(() -> controller.exportPersonalData(
                        Map.of(),
                        "owner-b",
                        "agent-b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authenticated ownerId is required");
    }

    @Test
    void knowledgeControllerRejectsMemoryAgentMismatch() {
        KnowledgeController controller = new KnowledgeController(
                mock(com.harnessagent.rag.application.KnowledgeService.class),
                mock(PersonalMemoryService.class),
                new ApiIdentityResolver());

        assertThatThrownBy(() -> controller.listMemories(
                        Map.of(
                                "X-Owner-Id", "owner-a",
                                "X-Agent-Id", "agent-a"),
                        "owner-a",
                        "agent-b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agentId");
    }

    @Test
    void toolControllerRequiresTrustedAgentIdentityForExecution() {
        ToolController controller = new ToolController(
                mock(com.harnessagent.tooling.application.ToolService.class),
                new ApiIdentityResolver());
        ToolExecutionApiRequest request = new ToolExecutionApiRequest(
                "owner-a",
                "agent-b",
                "session-a",
                "tool-a",
                Map.of(),
                false,
                null);

        assertThatThrownBy(() -> controller.execute(
                        Map.of(
                                "X-Owner-Id", "owner-a",
                                "X-Agent-Id", "agent-a"),
                        request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agentId");
    }

    @Test
    void sessionControllerUsesOwnerAgentAndSessionScope() {
        SessionStore sessionStore = mock(SessionStore.class);
        ChatMessage message = ChatMessage.assistant("hello");
        when(sessionStore.listMessages(any())).thenReturn(List.of(message));
        SessionController controller = new SessionController(
                new RuntimeContextFactory(),
                sessionStore,
                new ApiIdentityResolver());

        List<ChatMessage> messages = controller.listMessages(
                Map.of("X-Owner-Id", "owner-a"),
                "owner-a",
                "agent-a",
                "session-a");

        ArgumentCaptor<RuntimeContextScope> context = ArgumentCaptor.forClass(RuntimeContextScope.class);
        verify(sessionStore).listMessages(context.capture());
        assertThat(messages).containsExactly(message);
        assertThat(context.getValue().ownerId()).isEqualTo("owner-a");
        assertThat(context.getValue().agentId()).isEqualTo("agent-a");
        assertThat(context.getValue().sessionId()).isEqualTo("session-a");
        assertThat(context.getValue().runtimeUserId()).isEqualTo("owner:owner-a");
    }

    @Test
    void orchestrationControllerPassesDelegationAndFailureControls() {
        OrchestrationService orchestrationService = mock(OrchestrationService.class);
        ApiIdentityResolver identityResolver = mock(ApiIdentityResolver.class);
        OwnerPrincipal principal = new OwnerPrincipal(
                "owner-scope-a",
                "trusted-user",
                IdentityProviderType.INTERNAL);
        when(identityResolver.resolve(any(), any()))
                .thenReturn(principal);
        when(orchestrationService.orchestrate(any()))
                .thenReturn(new OrchestrationResult(
                        new RouteDecision("", 0d, OrchestrationStatus.ESCALATED, "test"),
                        new OrchestrationTrace(
                                null,
                                Instant.now(),
                                "owner-scope-a",
                                "trusted-user",
                                "supervisor",
                                "",
                                "intent",
                                0d,
                                OrchestrationStatus.ESCALATED,
                                List.of(),
                                List.of(),
                                List.of(),
                                Map.of())));
        OrchestrationController controller = new OrchestrationController(orchestrationService, identityResolver);

        controller.route(
                Map.of(),
                new OrchestrationApiRequest(
                        "body-user",
                        "supervisor",
                        "intent",
                        "task",
                        Map.of("question", "q"),
                        DelegationMode.BACKGROUND,
                        FailureStrategy.FALLBACK_TO_SUPERVISOR));

        ArgumentCaptor<OrchestrationRequest> command = ArgumentCaptor.forClass(OrchestrationRequest.class);
        verify(orchestrationService).orchestrate(command.capture());
        assertThat(command.getValue().principal()).isEqualTo(principal);
        assertThat(command.getValue().delegationMode()).isEqualTo(DelegationMode.BACKGROUND);
        assertThat(command.getValue().failureStrategy()).isEqualTo(FailureStrategy.FALLBACK_TO_SUPERVISOR);
    }

    @Test
    void streamEventPayloadKeepsClientVisibleFields() throws Exception {
        KnowledgeCitation citation = new KnowledgeCitation("source-a", "Title", "v1", 0, "chunk-a");
        StreamEventResponse response = new StreamEventResponse(
                "done",
                "COMPLETION",
                "USER_VISIBLE",
                "answer",
                true,
                "no accessible evidence",
                List.of(citation),
                Map.of("noAnswerReason", "no accessible evidence", "tokenCount", 12));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder(
                        "type",
                        "kind",
                        "channel",
                        "content",
                        "terminal",
                        "noAnswerReason",
                        "citations",
                        "metadata");
        assertThat(json.get("type").asText()).isEqualTo("done");
        assertThat(json.get("kind").asText()).isEqualTo("COMPLETION");
        assertThat(json.get("channel").asText()).isEqualTo("USER_VISIBLE");
        assertThat(json.get("terminal").asBoolean()).isTrue();
        assertThat(json.get("metadata").get("tokenCount").asInt()).isEqualTo(12);
        assertThat(AgentRuntimeEventType.STATUS.name().toLowerCase()).isEqualTo("status");
        assertThat(AgentRuntimeEventType.DELTA.name().toLowerCase()).isEqualTo("delta");
        assertThat(AgentRuntimeEventType.TOOL.name().toLowerCase()).isEqualTo("tool");
        assertThat(AgentRuntimeEventType.SUBAGENT.name().toLowerCase()).isEqualTo("subagent");
        assertThat(AgentRuntimeEventType.ERROR.name().toLowerCase()).isEqualTo("error");
        assertThat(AgentRuntimeEventType.DONE.name().toLowerCase()).isEqualTo("done");
        assertThat(StreamEventKind.from(AgentRuntimeEventType.STATUS)).isEqualTo(StreamEventKind.MODEL_STATUS);
        assertThat(StreamEventKind.from(AgentRuntimeEventType.DELTA)).isEqualTo(StreamEventKind.TEXT_DELTA);
        assertThat(StreamEventKind.from(AgentRuntimeEventType.TOOL)).isEqualTo(StreamEventKind.TOOL_EVENT);
        assertThat(StreamEventKind.from(AgentRuntimeEventType.SUBAGENT)).isEqualTo(StreamEventKind.SUBAGENT_EVENT);
        assertThat(StreamEventKind.from(AgentRuntimeEventType.ERROR)).isEqualTo(StreamEventKind.ERROR);
        assertThat(StreamEventKind.from(AgentRuntimeEventType.DONE)).isEqualTo(StreamEventKind.COMPLETION);
        assertThat(new StreamEventResponse("error", "stream failed", true).kind()).isEqualTo("ERROR");
    }

    @Test
    void chatMessageJsonIncludesContentBlocks() throws Exception {
        ChatMessage message = new ChatMessage(
                "message-1",
                MessageRole.ASSISTANT,
                List.of(
                        ContentBlock.text("summary"),
                        ContentBlock.image("workspace://files/chart.png", "image/png", "chart.png"),
                        ContentBlock.toolResult("search.docs", Map.of("matches", 2))),
                Instant.parse("2026-06-15T08:00:00Z"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(message));

        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "role", "content", "contentBlocks", "createdAt");
        assertThat(json.get("content").asText()).isEqualTo("summary");
        assertThat(json.get("contentBlocks").get(1).get("type").asText()).isEqualTo("IMAGE");
        assertThat(json.get("contentBlocks").get(1).get("uri").asText()).isEqualTo("workspace://files/chart.png");
        assertThat(json.get("contentBlocks").get(2).get("metadata").get("toolName").asText()).isEqualTo("search.docs");
    }

    @Test
    void errorHandlerKeepsStatusAndBodyShape() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<ErrorResponse> badRequest = handler.handleBadRequest(new IllegalArgumentException("bad input"));
        ResponseEntity<ErrorResponse> conflict = handler.handleInvalidState(new IllegalStateException("conflict"));

        assertThat(badRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badRequest.getBody()).isNotNull();
        assertThat(badRequest.getBody().message()).isEqualTo("bad input");
        assertThat(badRequest.getBody().timestamp()).isNotNull();
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).isNotNull();
        assertThat(conflict.getBody().message()).isEqualTo("conflict");
        assertThat(conflict.getBody().timestamp()).isNotNull();
    }

    @Test
    void personalAndLegacyRestUrlInventoryRemainStable() throws Exception {
        assertRootMapping(ToolController.class, "/api/tools");
        assertPostMapping(ToolController.class, "register", new String[0]);
        assertGetMapping(ToolController.class, "list", new String[0]);
        assertPostMapping(ToolController.class, "execute", new String[] {"/execute"});
        assertPostMapping(ToolController.class, "reject", new String[] {"/reject"});
        assertGetMapping(ToolController.class, "listPendingConfirmations", new String[] {"/confirmations"});
        assertPostMapping(ToolController.class, "resumeConfirmation", new String[] {"/confirmations/{confirmationId}/resume"});
        assertGetMapping(ToolController.class, "listActivity", new String[] {"/activity"});

        assertRootMapping(KnowledgeController.class, "/api/knowledge");
        assertPostMapping(KnowledgeController.class, "registerSource", new String[] {"/sources"});
        assertGetMapping(KnowledgeController.class, "listSources", new String[] {"/sources"});
        assertPostMapping(KnowledgeController.class, "ingestDocument", new String[] {"/documents"});
        assertPostMapping(KnowledgeController.class, "retrieve", new String[] {"/retrieve"});
        assertPostMapping(KnowledgeController.class, "revokeSource", new String[] {"/sources/{sourceId}/revoke"});
        assertDeleteMapping(KnowledgeController.class, "deleteSource", new String[] {"/sources/{sourceId}"});
        assertGetMapping(KnowledgeController.class, "listMemories", new String[] {"/memory"});
        assertPostMapping(KnowledgeController.class, "requestMemoryWrite", new String[] {"/memory"});
        assertPostMapping(KnowledgeController.class, "confirmMemoryWrite", new String[] {"/memory/{memoryId}/confirm"});
        assertPostMapping(KnowledgeController.class, "rejectMemoryWrite", new String[] {"/memory/{memoryId}/reject"});
        assertDeleteMapping(KnowledgeController.class, "deleteMemory", new String[] {"/memory/{memoryId}"});
        assertGetMapping(KnowledgeController.class, "exportPersonalData", new String[] {"/export"});
        assertGetMapping(KnowledgeController.class, "listMetrics", new String[] {"/metrics"});
        assertPostMapping(KnowledgeController.class, "recordFeedback", new String[] {"/feedback"});
        assertGetMapping(KnowledgeController.class, "listFeedback", new String[] {"/feedback"});

        assertRootMapping(SessionController.class, "/api");
        assertGetMapping(SessionController.class, "listSessions", new String[] {"/sessions"});
        assertGetMapping(SessionController.class, "listMessages", new String[] {"/messages"});
        assertDeleteMapping(SessionController.class, "deleteSession", new String[] {"/sessions/{sessionId}"});

        assertRootMapping(ReadinessController.class, "/api/diagnostics/readiness");
        assertGetMapping(ReadinessController.class, "scenario", new String[] {"/scenario"});
        assertGetMapping(ReadinessController.class, "readinessChecks", new String[] {"/readiness-checks"});
        assertGetMapping(ReadinessController.class, "rollbackActions", new String[] {"/rollback"});
        assertGetMapping(ReadinessController.class, "acceptance", new String[] {"/acceptance"});

        assertRootMapping(OrchestrationController.class, "/api/orchestration");
        assertPostMapping(OrchestrationController.class, "register", new String[] {"/agents"});
        assertPostMapping(OrchestrationController.class, "route", new String[] {"/route"});
        assertGetMapping(OrchestrationController.class, "asTool", new String[] {"/agents/{agentId}/tool"});
        assertGetMapping(OrchestrationController.class, "traces", new String[] {"/traces"});

        assertRootMapping(SkillController.class, "/api/skills");
        assertGetMapping(SkillController.class, "listSkills", new String[0]);
        assertPostMapping(SkillController.class, "refreshLocalRepository", new String[] {"/refresh-local"});
        assertPostMapping(SkillController.class, "validateLocalSkill", new String[] {"/validate-local"});
        assertPatchMapping(SkillController.class, "enable", new String[] {"/{skillName}/{version}/enable"});
        assertPatchMapping(SkillController.class, "disable", new String[] {"/{skillName}/{version}/disable"});
        assertPatchMapping(SkillController.class, "upgrade", new String[] {"/{skillName}/{version}/upgrade"});
        assertPatchMapping(SkillController.class, "rollback", new String[] {"/{skillName}/{version}/rollback"});
        assertPatchMapping(SkillController.class, "lock", new String[] {"/{skillName}/{version}/lock"});
    }

    @Test
    void legacyConsoleUrlInventoryRemainsStable() throws Exception {
        assertRootMapping(ConsoleController.class, "/api/console");
        assertGetMapping(ConsoleController.class, "userConsole", new String[] {"/user"});
        assertGetMapping(ConsoleController.class, "listAgents", new String[] {"/agents"});
        assertPatchMapping(ConsoleController.class, "updateAgentPrompt", new String[] {"/agents/{agentId}/prompt"});
        assertPatchMapping(ConsoleController.class, "updateAgentConfig", new String[] {"/agents/{agentId}/config"});
        assertGetMapping(ConsoleController.class, "listTools", new String[] {"/tools"});
        assertPatchMapping(ConsoleController.class, "setToolEnabled", new String[] {"/tools/{toolId}/enabled"});
        assertGetMapping(ConsoleController.class, "listKnowledge", new String[] {"/knowledge"});
        assertPatchMapping(ConsoleController.class, "revokeKnowledge", new String[] {"/knowledge/{sourceId}/revoke"});
        assertGetMapping(ConsoleController.class, "listSkills", new String[] {"/skills"});
        assertGetMapping(ConsoleController.class, "metrics", new String[] {"/metrics"});
        assertGetMapping(ConsoleController.class, "cost", new String[] {"/cost"});
        assertGetMapping(ConsoleController.class, "recordActivity", new String[] {"/activity"});
    }

    private static void assertRootMapping(Class<?> controller, String path) {
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly(path);
    }

    private static void assertPostMapping(Class<?> controller, String method, String[] paths) throws Exception {
        assertPostMapping(controller, method, paths, new String[0]);
    }

    private static void assertPostMapping(Class<?> controller, String method, String[] paths, String[] produces)
            throws Exception {
        PostMapping mapping = method(controller, method).getAnnotation(PostMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(paths(mapping.value(), mapping.path())).containsExactly(paths);
        assertThat(mapping.produces()).containsExactly(produces);
    }

    private static void assertGetMapping(Class<?> controller, String method, String[] paths) throws Exception {
        GetMapping mapping = method(controller, method).getAnnotation(GetMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(paths(mapping.value(), mapping.path())).containsExactly(paths);
    }

    private static void assertPatchMapping(Class<?> controller, String method, String[] paths) throws Exception {
        PatchMapping mapping = method(controller, method).getAnnotation(PatchMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(paths(mapping.value(), mapping.path())).containsExactly(paths);
    }

    private static void assertDeleteMapping(Class<?> controller, String method, String[] paths) throws Exception {
        DeleteMapping mapping = method(controller, method).getAnnotation(DeleteMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(paths(mapping.value(), mapping.path())).containsExactly(paths);
    }

    private static Method method(Class<?> controller, String name) {
        return List.of(controller.getDeclaredMethods()).stream()
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static String[] paths(String[] value, String[] path) {
        return value.length == 0 ? path : value;
    }
}
