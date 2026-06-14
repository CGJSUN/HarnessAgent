package com.harnessagent.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.agent.AgentReply;
import com.harnessagent.agent.AgentRunRequest;
import com.harnessagent.agent.AgentRuntime;
import com.harnessagent.agent.AgentRuntimeEvent;
import com.harnessagent.rag.InMemoryKnowledgeStore;
import com.harnessagent.rag.KnowledgeDocumentInput;
import com.harnessagent.rag.KnowledgeRetrievalPolicy;
import com.harnessagent.rag.KnowledgeService;
import com.harnessagent.rag.KnowledgeSourceRegistration;
import com.harnessagent.rag.KnowledgeVisibility;
import com.harnessagent.rag.TextChunker;
import com.harnessagent.rag.TextTokenizer;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.session.ChatMessage;
import com.harnessagent.session.InMemorySessionStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ChatServiceTest {

    private final RuntimeContextFactory contextFactory = new RuntimeContextFactory();
    private final InMemorySessionStore sessionStore = new InMemorySessionStore();
    private final RecordingAgentRuntime agentRuntime = new RecordingAgentRuntime();
    private final KnowledgeService knowledgeService = new KnowledgeService(
            new InMemoryKnowledgeStore(),
            new TextChunker(),
            new TextTokenizer(),
            new KnowledgeRetrievalPolicy());
    private final ChatService chatService =
            new ChatService(contextFactory, sessionStore, agentRuntime, knowledgeService);

    @Test
    void sendsDerivedRuntimeContextToAgentAndPersistsMessages() {
        ChatResult result = chatService.chat(command("hello")).block();

        assertThat(result.message()).isEqualTo("answer:hello");
        assertThat(agentRuntime.requests).hasSize(1);
        AgentRunRequest request = agentRuntime.requests.get(0);
        assertThat(request.context().runtimeUserId()).isEqualTo("tenant-a:user-a");
        assertThat(request.context().runtimeSessionId()).isEqualTo("agent-a:session-a");
        assertThat(sessionStore.listMessages(request.context()))
                .extracting(ChatMessage::content)
                .containsExactly("hello", "answer:hello");
    }

    @Test
    void streamsEventsAndPersistsAssistantMessageOnCompletion() {
        StepVerifier.create(chatService.stream(command("stream me")))
                .expectNextMatches(event -> event.content().equals("started"))
                .expectNextMatches(event -> event.content().equals("chunk-1"))
                .expectNextMatches(event -> event.content().equals("chunk-2"))
                .expectNextMatches(event -> event.content().equals("completed"))
                .verifyComplete();

        assertThat(sessionStore.listMessages(agentRuntime.requests.get(0).context()))
                .extracting(ChatMessage::content)
                .containsExactly("stream me", "chunk-1chunk-2");
    }

    @Test
    void injectsKnowledgeAndReturnsCitationsWhenEnabled() {
        knowledgeService.ingestDocument(new KnowledgeDocumentInput(
                new KnowledgeSourceRegistration(
                        "tenant-a",
                        "owner-a",
                        "报销制度",
                        "v1",
                        KnowledgeVisibility.PUBLIC,
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        "manual"),
                "发票需要在三十天内提交。"));

        ChatResult result = chatService.chat(new ChatCommand(
                "tenant-a",
                "user-a",
                "agent-a",
                "session-rag",
                "发票多久提交",
                true,
                Set.of(),
                Set.of(),
                3)).block();

        assertThat(result.knowledgeBacked()).isTrue();
        assertThat(result.citations()).hasSize(1);
        List<ChatMessage> agentMessages = agentRuntime.requests.get(0).messages();
        String augmentedPrompt = agentMessages.get(agentMessages.size() - 1).content();
        assertThat(augmentedPrompt)
                .contains("请仅基于以下可访问知识回答用户问题")
                .contains("发票需要在三十天内提交");
    }

    @Test
    void returnsNoAnswerWithoutCallingAgentWhenEvidenceIsMissing() {
        ChatResult result = chatService.chat(new ChatCommand(
                "tenant-a",
                "user-a",
                "agent-a",
                "session-no-answer",
                "没有知识的问题",
                true,
                Set.of(),
                Set.of(),
                3)).block();

        assertThat(result.noAnswerReason()).contains("无法从当前可用知识中确定答案");
        assertThat(agentRuntime.requests).isEmpty();
    }

    private static ChatCommand command(String message) {
        return new ChatCommand("tenant-a", "user-a", "agent-a", "session-a", message);
    }

    private static class RecordingAgentRuntime implements AgentRuntime {

        private final List<AgentRunRequest> requests = new ArrayList<>();

        @Override
        public Mono<AgentReply> complete(AgentRunRequest request) {
            requests.add(request);
            String lastMessage = request.messages().get(request.messages().size() - 1).content();
            return Mono.just(new AgentReply("answer:" + lastMessage));
        }

        @Override
        public Flux<AgentRuntimeEvent> stream(AgentRunRequest request) {
            requests.add(request);
            return Flux.just(
                    AgentRuntimeEvent.status("started"),
                    AgentRuntimeEvent.delta("chunk-1"),
                    AgentRuntimeEvent.delta("chunk-2"),
                    AgentRuntimeEvent.done("completed"));
        }
    }
}
