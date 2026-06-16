package com.harnessagent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.agent.runtime.AgentRuntimeEventType;
import com.harnessagent.api.controller.ApiExceptionHandler;
import com.harnessagent.api.controller.ChatController;
import com.harnessagent.api.controller.ConsoleController;
import com.harnessagent.api.controller.KnowledgeController;
import com.harnessagent.api.controller.OrchestrationController;
import com.harnessagent.api.controller.ReleaseController;
import com.harnessagent.api.controller.SessionController;
import com.harnessagent.api.controller.ToolController;
import com.harnessagent.api.request.ChatRequest;
import com.harnessagent.api.response.ChatResponse;
import com.harnessagent.api.response.ErrorResponse;
import com.harnessagent.api.response.StreamEventResponse;
import com.harnessagent.chat.domain.ChatCommand;
import com.harnessagent.chat.domain.ChatResult;
import com.harnessagent.chat.application.ChatService;
import com.harnessagent.rag.domain.KnowledgeCitation;
import com.harnessagent.security.domain.IdentityProviderType;
import com.harnessagent.security.domain.SecurityPrincipal;
import java.lang.reflect.Method;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        ChatResponse response = new ChatResponse("answer", "user-a", "session-a", true, null, List.of(citation));
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder(
                        "message",
                        "runtimeUserId",
                        "runtimeSessionId",
                        "knowledgeBacked",
                        "noAnswerReason",
                        "citations");
        assertThat(json.get("citations").get(0).get("sourceId").asText()).isEqualTo("source-a");
        assertThat(json.get("citations").get(0).get("chunkIndex").asInt()).isEqualTo(2);
    }

    @Test
    void chatControllerPreservesRequestIdentityAndResponseContract() {
        ChatService chatService = mock(ChatService.class);
        ApiIdentityResolver identityResolver = mock(ApiIdentityResolver.class);
        when(identityResolver.resolve(any(), any(), any(), any(), any()))
                .thenReturn(new SecurityPrincipal(
                        "tenant-a",
                        "trusted-user",
                        IdentityProviderType.INTERNAL,
                        Set.of("agent-user"),
                        Set.of("support")));
        when(chatService.chat(any()))
                .thenReturn(Mono.just(ChatResult.noAnswer("No accessible evidence.", "trusted-user", "session-a")));

        ChatController controller = new ChatController(chatService, identityResolver);
        ChatResponse response = controller.chat(
                Map.of(),
                new ChatRequest(
                        "tenant-a",
                        "body-user",
                        "enterprise-assistant",
                        "session-a",
                        "question",
                        true,
                        Set.of("body-dept"),
                        Set.of("body-role"),
                        3));

        ArgumentCaptor<ChatCommand> command = ArgumentCaptor.forClass(ChatCommand.class);
        verify(chatService).chat(command.capture());
        assertThat(command.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(command.getValue().userId()).isEqualTo("trusted-user");
        assertThat(command.getValue().departments()).containsExactly("support");
        assertThat(command.getValue().roles()).containsExactly("agent-user");
        assertThat(command.getValue().knowledgeEnabled()).isTrue();
        assertThat(command.getValue().knowledgeLimit()).isEqualTo(3);

        assertThat(response.message()).isEqualTo("No accessible evidence.");
        assertThat(response.runtimeUserId()).isEqualTo("trusted-user");
        assertThat(response.runtimeSessionId()).isEqualTo("session-a");
        assertThat(response.knowledgeBacked()).isFalse();
        assertThat(response.noAnswerReason()).isEqualTo("No accessible evidence.");
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void streamEventPayloadKeepsClientVisibleFields() throws Exception {
        KnowledgeCitation citation = new KnowledgeCitation("source-a", "Title", "v1", 0, "chunk-a");
        StreamEventResponse response = new StreamEventResponse(
                "done",
                "answer",
                true,
                "no accessible evidence",
                List.of(citation),
                Map.of("noAnswerReason", "no accessible evidence", "tokenCount", 12));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder(
                        "type",
                        "content",
                        "terminal",
                        "noAnswerReason",
                        "citations",
                        "metadata");
        assertThat(json.get("type").asText()).isEqualTo("done");
        assertThat(json.get("terminal").asBoolean()).isTrue();
        assertThat(json.get("metadata").get("tokenCount").asInt()).isEqualTo(12);
        assertThat(AgentRuntimeEventType.STATUS.name().toLowerCase()).isEqualTo("status");
        assertThat(AgentRuntimeEventType.DELTA.name().toLowerCase()).isEqualTo("delta");
        assertThat(AgentRuntimeEventType.TOOL.name().toLowerCase()).isEqualTo("tool");
        assertThat(AgentRuntimeEventType.ERROR.name().toLowerCase()).isEqualTo("error");
        assertThat(AgentRuntimeEventType.DONE.name().toLowerCase()).isEqualTo("done");
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
    void restUrlInventoryRemainsStable() throws Exception {
        assertRootMapping(ToolController.class, "/api/tools");
        assertPostMapping(ToolController.class, "register", new String[0]);
        assertGetMapping(ToolController.class, "list", new String[0]);
        assertPostMapping(ToolController.class, "execute", new String[] {"/execute"});
        assertPostMapping(ToolController.class, "reject", new String[] {"/reject"});
        assertGetMapping(ToolController.class, "listAudit", new String[] {"/audit"});

        assertRootMapping(KnowledgeController.class, "/api/knowledge");
        assertPostMapping(KnowledgeController.class, "registerSource", new String[] {"/sources"});
        assertGetMapping(KnowledgeController.class, "listSources", new String[] {"/sources"});
        assertPostMapping(KnowledgeController.class, "ingestDocument", new String[] {"/documents"});
        assertPostMapping(KnowledgeController.class, "retrieve", new String[] {"/retrieve"});
        assertPostMapping(KnowledgeController.class, "revokeSource", new String[] {"/sources/{sourceId}/revoke"});
        assertDeleteMapping(KnowledgeController.class, "deleteSource", new String[] {"/sources/{sourceId}"});
        assertGetMapping(KnowledgeController.class, "listMetrics", new String[] {"/metrics"});
        assertPostMapping(KnowledgeController.class, "recordFeedback", new String[] {"/feedback"});
        assertGetMapping(KnowledgeController.class, "listFeedback", new String[] {"/feedback"});

        assertRootMapping(SessionController.class, "/api");
        assertGetMapping(SessionController.class, "listSessions", new String[] {"/sessions"});
        assertGetMapping(SessionController.class, "listMessages", new String[] {"/messages"});
        assertDeleteMapping(SessionController.class, "deleteSession", new String[] {"/sessions/{sessionId}"});

        assertRootMapping(ReleaseController.class, "/api/release");
        assertGetMapping(ReleaseController.class, "scenario", new String[] {"/scenario"});
        assertGetMapping(ReleaseController.class, "phaseGates", new String[] {"/phase-gates"});
        assertGetMapping(ReleaseController.class, "rollbackActions", new String[] {"/rollback"});
        assertGetMapping(ReleaseController.class, "acceptance", new String[] {"/acceptance"});

        assertRootMapping(OrchestrationController.class, "/api/orchestration");
        assertPostMapping(OrchestrationController.class, "register", new String[] {"/agents"});
        assertPostMapping(OrchestrationController.class, "route", new String[] {"/route"});
        assertGetMapping(OrchestrationController.class, "asTool", new String[] {"/agents/{agentId}/tool"});
        assertGetMapping(OrchestrationController.class, "traces", new String[] {"/traces"});
    }

    @Test
    void consoleUrlInventoryRemainsStable() throws Exception {
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
        assertPatchMapping(ConsoleController.class, "approveSkill", new String[] {"/skills/{versionId}/approve"});
        assertPatchMapping(ConsoleController.class, "publishSkill", new String[] {"/skills/{versionId}/publish"});
        assertPatchMapping(ConsoleController.class, "disableSkill", new String[] {"/skills/{versionId}/disable"});
        assertGetMapping(ConsoleController.class, "metrics", new String[] {"/metrics"});
        assertGetMapping(ConsoleController.class, "cost", new String[] {"/cost"});
        assertGetMapping(ConsoleController.class, "audit", new String[] {"/audit"});
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
