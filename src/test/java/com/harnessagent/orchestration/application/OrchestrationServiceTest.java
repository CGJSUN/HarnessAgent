package com.harnessagent.orchestration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harnessagent.agent.runtime.AgentReply;
import com.harnessagent.agent.runtime.AgentRunRequest;
import com.harnessagent.agent.runtime.AgentRuntime;
import com.harnessagent.agent.runtime.AgentRuntimeEvent;
import com.harnessagent.production.telemetry.RuntimeTelemetry;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.security.domain.IdentityProviderType;
import com.harnessagent.security.domain.OwnerPrincipal;
import com.harnessagent.security.application.SensitiveDataRedactor;
import com.harnessagent.tooling.persistence.InMemoryToolStore;
import com.harnessagent.tooling.application.ToolService;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolExecutionStatus;
import com.harnessagent.tooling.domain.ToolRiskLevel;
import com.harnessagent.tooling.execution.ToolExecutionCommand;
import com.harnessagent.tooling.execution.ToolExecutionResult;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.harnessagent.orchestration.application.AgentAsToolExecutor;
import com.harnessagent.orchestration.application.ExpertAgentRegistry;
import com.harnessagent.orchestration.application.OrchestrationTraceStore;
import com.harnessagent.orchestration.application.OrchestrationService;
import com.harnessagent.orchestration.application.SupervisorRouter;
import com.harnessagent.orchestration.domain.AgentToolDefinition;
import com.harnessagent.orchestration.domain.ExpertAgentDefinition;
import com.harnessagent.orchestration.domain.ContextBoundary;
import com.harnessagent.orchestration.domain.DelegationMode;
import com.harnessagent.orchestration.domain.FailureStrategy;
import com.harnessagent.orchestration.domain.OrchestrationRequest;
import com.harnessagent.orchestration.domain.OrchestrationResult;
import com.harnessagent.orchestration.domain.OrchestrationStatus;
import com.harnessagent.orchestration.domain.OrchestrationStep;

class OrchestrationServiceTest {

    private final ExpertAgentRegistry registry = new ExpertAgentRegistry();
    private final RecordingAgentRuntime agentRuntime = new RecordingAgentRuntime();
    private final RuntimeContextFactory contextFactory = new RuntimeContextFactory();
    private final OrchestrationTraceStore traceStore = new OrchestrationTraceStore();
    private final ToolService toolService = new ToolService(new InMemoryToolStore(), java.util.List.of(
            new AgentAsToolExecutor(registry, agentRuntime, contextFactory, traceStore)));
    private final OrchestrationService service = new OrchestrationService(
            registry,
            new SupervisorRouter(),
            agentRuntime,
            contextFactory,
            toolService,
            RuntimeTelemetry.noop(),
            new SensitiveDataRedactor(),
            traceStore);

    @Test
    void registersSubAgentSpecWithSkillsAndContextBoundary() {
        ExpertAgentDefinition agent = service.register(agent(
                "writer-agent",
                "撰写个人文档",
                Set.of("user-a"),
                Set.of("workspace.write"),
                Set.of("writing-skill"),
                Set.of("knowledge-a"),
                new ContextBoundary(false, true, false, true, Set.of("question", "draftPath")),
                true));

        assertThat(agent.allowedSkills()).containsExactly("writing-skill");
        assertThat(agent.contextBoundary().shareFiles()).isTrue();
        assertThat(agent.contextBoundary().allowedKeys()).containsExactlyInAnyOrder("question", "draftPath");
    }

    @Test
    void routesByIntentCapabilityAuthorizationAndContextBoundary() {
        service.register(agent(
                "research-light",
                "研究资料",
                Set.of("user-a"),
                Set.of("search"),
                Set.of(),
                Set.of(),
                new ContextBoundary(false, false, false, false, Set.of("question")),
                true));
        ExpertAgentDefinition agent = service.register(agent(
                "knowledge-agent",
                "研究资料并总结引用来源",
                Set.of("user-a"),
                Set.of("search"),
                Set.of("research-skill"),
                Set.of("knowledge-a"),
                new ContextBoundary(false, false, false, true, Set.of("question", "citations")),
                true));

        OrchestrationResult result = service.orchestrate(new OrchestrationRequest(
                principal(Set.of("user-a")),
                "supervisor",
                "研究资料",
                "总结资料",
                Map.of("question", "报销制度", "citations", java.util.List.of("doc-1"), "secret", "不应共享"),
                DelegationMode.SYNC,
                FailureStrategy.STOP));

        assertThat(result.decision().selectedAgentId()).isEqualTo(agent.id());
        assertThat(result.trace().steps())
                .extracting(step -> step.action())
                .contains("route", "handoff", "execute_task", "assemble_result");
        assertThat(agentRuntime.requests).hasSize(1);
        assertThat(result.trace().handoffs().get(0).sharedContext())
                .containsKeys("question", "citations")
                .doesNotContainKey("secret");
        assertThat(result.trace().attributes()).containsKey("candidateReasons");
    }

    @Test
    void routesToLeastPrivilegedBoundaryCompatibleAgent() {
        service.register(agent(
                "broad-summary-agent",
                "整理报告",
                Set.of("user-a"),
                Set.of("workspace.write", "shell.command"),
                Set.of("report-skill"),
                Set.of("knowledge-a"),
                new ContextBoundary(false, false, false, false, Set.of("question")),
                true));
        ExpertAgentDefinition narrow = service.register(agent(
                "narrow-summary-agent",
                "整理报告",
                Set.of("user-a"),
                Set.of(),
                Set.of(),
                Set.of(),
                new ContextBoundary(false, false, false, false, Set.of("question")),
                true));

        OrchestrationResult result = service.orchestrate(new OrchestrationRequest(
                principal(Set.of("user-a")),
                "supervisor",
                "整理报告",
                "整理报告",
                Map.of("question", "本周进展"),
                DelegationMode.SYNC,
                FailureStrategy.STOP));

        assertThat(result.decision().selectedAgentId()).isEqualTo(narrow.id());
    }

    @Test
    void exposesApprovedChildAgentAsTool() {
        ExpertAgentDefinition agent = service.register(agent(
                "approval-agent",
                "处理审批申请",
                Set.of("user-a"),
                true));

        AgentToolDefinition tool = service.asTool(agent.id());

        assertThat(tool.toolName()).isEqualTo("agent.approval-agent");
        assertThat(tool.allowedOwnerIds()).contains("user-a");
        assertThat(toolService.listTools("owner-scope-a")).hasSize(1);
    }

    @Test
    void executesAgentAsToolThroughToolGovernanceAndRecordsTraceReference() {
        ExpertAgentDefinition agent = service.register(agent(
                "review-agent",
                "代码审查",
                Set.of("user-a"),
                Set.of("repo.read"),
                Set.of("code-review-skill"),
                Set.of(),
                new ContextBoundary(false, false, false, false, Set.of("task", "question")),
                true));
        service.asTool(agent.id());
        ToolDefinition tool = toolService.listTools("owner-scope-a").get(0);

        ToolExecutionResult result = toolService.execute(new ToolExecutionCommand(
                "owner-scope-a",
                "user-a",
                "supervisor",
                "session-a",
                tool.id(),
                Map.of("task", "review patch", "context", Map.of("question", "check risk")),
                Set.of(),
                Set.of("user-a"),
                false,
                null,
                null,
                null));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        assertThat(result.output()).containsEntry("childAgentId", agent.id());
        assertThat(result.output()).containsEntry("result", "child-answer");
        assertThat(result.output()).containsKey("traceId");
        assertThat(service.listTraces(principal(Set.of("user-a"))))
                .extracting(trace -> trace.id())
                .contains(String.valueOf(result.output().get("traceId")));
        assertThat(toolService.listActivity("owner-scope-a"))
                .singleElement()
                .satisfies(record -> assertThat(record.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED));
    }

    @Test
    void registersMutatingChildAgentToolAsHighRisk() {
        ExpertAgentDefinition agent = service.register(agent(
                "workspace-agent",
                "修改工作区文件",
                Set.of("user-a"),
                Set.of("workspace.write"),
                Set.of(),
                Set.of(),
                new ContextBoundary(false, true, false, false, Set.of("task", "path")),
                true));

        ToolDefinition tool = service.registerAgentAsTool(agent.id());

        assertThat(tool.riskLevel()).isEqualTo(ToolRiskLevel.HIGH_RISK);
        assertThat(tool.mutating()).isTrue();
    }

    @Test
    void agentAsToolFailureStillStoresBlockedTrace() {
        ExpertAgentDefinition agent = service.register(agent(
                "failing-review-agent",
                "代码审查",
                Set.of("user-a"),
                Set.of("repo.read"),
                Set.of(),
                Set.of(),
                new ContextBoundary(false, false, false, false, Set.of("task")),
                true));
        agentRuntime.failOnceFor(agent.id());
        service.asTool(agent.id());
        ToolDefinition tool = toolService.listTools("owner-scope-a").get(0);

        ToolExecutionResult result = toolService.execute(new ToolExecutionCommand(
                "owner-scope-a",
                "user-a",
                "supervisor",
                "session-a",
                tool.id(),
                Map.of("task", "review patch"),
                Set.of(),
                Set.of("user-a"),
                false,
                null,
                null,
                null));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.FAILED);
        assertThat(service.listTraces(principal(Set.of("user-a"))))
                .anySatisfy(trace -> {
                    assertThat(trace.selectedAgentId()).isEqualTo(agent.id());
                    assertThat(trace.status()).isEqualTo(OrchestrationStatus.BLOCKED);
                    assertThat(trace.steps())
                            .extracting(OrchestrationStep::action)
                            .contains("agent_as_tool_failed");
                });
    }

    @Test
    void backgroundDelegationReturnsBeforeChildCompletesAndStoresTerminalTrace() throws Exception {
        BlockingAgentRuntime blockingRuntime = new BlockingAgentRuntime();
        OrchestrationTraceStore backgroundTraceStore = new OrchestrationTraceStore();
        OrchestrationService backgroundService = serviceWith(blockingRuntime, backgroundTraceStore);
        ExpertAgentDefinition agent = service.register(agent(
                "research-agent",
                "后台研究",
                Set.of("user-a"),
                Set.of("search"),
                Set.of("research-skill"),
                Set.of("knowledge-a"),
                new ContextBoundary(false, false, false, true, Set.of("question")),
                true));
        backgroundService.register(agent);

        CompletableFuture<OrchestrationResult> future = CompletableFuture.supplyAsync(() -> backgroundService.orchestrate(
                new OrchestrationRequest(
                principal(Set.of("user-a")),
                "supervisor",
                "后台研究",
                "后台收集资料",
                Map.of("question", "资料"),
                DelegationMode.BACKGROUND,
                FailureStrategy.STOP)));

        assertThat(blockingRuntime.childStarted.await(1, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(future.isDone()).isTrue();
        } finally {
            blockingRuntime.readiness();
        }
        OrchestrationResult result = future.get(2, TimeUnit.SECONDS);

        assertThat(result.decision().selectedAgentId()).isEqualTo(agent.id());
        assertThat(result.trace().status().name()).isEqualTo("BACKGROUND_RUNNING");
        assertThat(result.trace().steps())
                .extracting(OrchestrationStep::action)
                .contains("background_delegate")
                .doesNotContain("background_complete");
        assertThat(eventuallyHasBackgroundCompletion(backgroundService, agent.id())).isTrue();
    }

    @Test
    void handoffContextBoundaryRemovesMemoryFilesAndToolOutputs() {
        service.register(agent(
                "privacy-agent",
                "处理隐私问题",
                Set.of("user-a"),
                Set.of(),
                Set.of(),
                Set.of(),
                new ContextBoundary(false, false, false, false, Set.of("question")),
                true));

        OrchestrationResult result = service.orchestrate(new OrchestrationRequest(
                principal(Set.of("user-a")),
                "supervisor",
                "隐私",
                "回答问题",
                Map.of(
                        "question", "可以共享",
                        "privateMemory", "不应共享",
                        "workspaceFile", "不应共享",
                        "toolResult", "不应共享",
                        "citations", java.util.List.of("不应共享")),
                DelegationMode.SYNC,
                FailureStrategy.STOP));

        assertThat(result.trace().handoffs().get(0).sharedContext())
                .containsOnly(Map.entry("question", "可以共享"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handoffContextBoundaryRemovesNestedMemoryFilesAndToolOutputs() {
        service.register(agent(
                "privacy-agent",
                "处理隐私问题",
                Set.of("user-a"),
                Set.of(),
                Set.of(),
                Set.of(),
                new ContextBoundary(false, false, false, false, Set.of("payload")),
                true));

        OrchestrationResult result = service.orchestrate(new OrchestrationRequest(
                principal(Set.of("user-a")),
                "supervisor",
                "隐私",
                "回答问题",
                Map.of("payload", Map.of(
                        "question", "可以共享",
                        "privateMemory", "不应共享",
                        "workspaceFile", "不应共享",
                        "toolOutput", "不应共享",
                        "citations", java.util.List.of("不应共享"))),
                DelegationMode.SYNC,
                FailureStrategy.STOP));

        Map<String, Object> payload =
                (Map<String, Object>) result.trace().handoffs().get(0).sharedContext().get("payload");
        assertThat(payload).containsOnly(Map.entry("question", "可以共享"));
    }

    @Test
    void fallsBackToSupervisorWhenChildAgentFails() {
        ExpertAgentDefinition agent = service.register(agent(
                "unstable-agent",
                "失败后回退",
                Set.of("user-a"),
                Set.of(),
                Set.of(),
                Set.of(),
                new ContextBoundary(false, false, false, false, Set.of("question")),
                true));
        agentRuntime.failOnceFor(agent.id());
        agentRuntime.reply("supervisor", "supervisor fallback");

        OrchestrationResult result = service.orchestrate(new OrchestrationRequest(
                principal(Set.of("user-a")),
                "supervisor",
                "失败后回退",
                "执行可能失败的任务",
                Map.of("question", "q"),
                DelegationMode.SYNC,
                FailureStrategy.FALLBACK_TO_SUPERVISOR));

        assertThat(result.trace().status()).isEqualTo(OrchestrationStatus.EXECUTED);
        assertThat(result.trace().attributes()).containsEntry("fallbackAgentId", "supervisor");
        assertThat(result.trace().steps())
                .extracting(step -> step.action())
                .contains("execute_task_failed", "fallback_to_supervisor", "assemble_result");
        assertThat(agentRuntime.requests)
                .extracting(request -> request.context().agentId())
                .containsExactly(agent.id(), "supervisor");
    }

    @Test
    void fallbackFailureReturnsBlockedTraceInsteadOfThrowing() {
        ExpertAgentDefinition agent = service.register(agent(
                "unstable-agent",
                "失败后回退",
                Set.of("user-a"),
                Set.of(),
                Set.of(),
                Set.of(),
                new ContextBoundary(false, false, false, false, Set.of("question")),
                true));
        agentRuntime.failOnceFor(agent.id());
        agentRuntime.failAlwaysFor("supervisor");

        OrchestrationResult result = service.orchestrate(new OrchestrationRequest(
                principal(Set.of("user-a")),
                "supervisor",
                "失败后回退",
                "执行可能失败的任务",
                Map.of("question", "q"),
                DelegationMode.SYNC,
                FailureStrategy.FALLBACK_TO_SUPERVISOR));

        assertThat(result.trace().status()).isEqualTo(OrchestrationStatus.BLOCKED);
        assertThat(result.trace().steps())
                .extracting(OrchestrationStep::action)
                .contains("execute_task_failed", "fallback_to_supervisor_failed");
        assertThat(result.trace().attributes()).containsEntry("nextAction", "stop");
    }

    @Test
    void rejectsUnapprovedChildAgentAsTool() {
        ExpertAgentDefinition agent = service.register(agent(
                "draft-agent",
                "草稿 Agent",
                Set.of("user-a"),
                Set.of(),
                Set.of(),
                Set.of(),
                new ContextBoundary(false, false, false, false, Set.of("question")),
                false));

        assertThatThrownBy(() -> service.asTool(agent.id()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void escalatesWhenNoPermittedAgentCanHandleTask() {
        service.register(agent("finance-agent", "财务专用", Set.of("finance"), true));

        OrchestrationResult result = service.orchestrate(new OrchestrationRequest(
                principal(Set.of("user-a")),
                "supervisor",
                "财务",
                "处理薪资",
                Map.of("question", "薪资"),
                DelegationMode.SYNC,
                FailureStrategy.CLARIFY));

        assertThat(result.decision().status()).isEqualTo(OrchestrationStatus.ESCALATED);
        assertThat(result.trace().status()).isEqualTo(OrchestrationStatus.ESCALATED);
        assertThat(result.trace().attributes()).containsEntry("nextAction", "clarify");
    }

    @Test
    void traceSearchReturnsPersonalOwnerScopeTraces() {
        service.register(agent("research-agent", "研究资料", Set.of("user-a"), true));

        OrchestrationResult result = service.orchestrate(new OrchestrationRequest(
                principal(Set.of("user-a")),
                "supervisor",
                "研究资料",
                "总结资料",
                Map.of("question", "资料"),
                DelegationMode.SYNC,
                FailureStrategy.STOP));

        assertThat(service.listTraces(principal(Set.of("user-a"))))
                .extracting(trace -> trace.id())
                .contains(result.trace().id());
    }

    private static ExpertAgentDefinition agent(
            String name,
            String purpose,
            Set<String> owners,
            boolean approved) {
        return agent(
                name,
                purpose,
                owners,
                Set.of("tool-a"),
                Set.of(),
                Set.of("knowledge-a"),
                new ContextBoundary(false, false, false, true, Set.of("question", "citations")),
                approved);
    }

    private static ExpertAgentDefinition agent(
            String name,
            String purpose,
            Set<String> owners,
            Set<String> tools,
            Set<String> skills,
            Set<String> knowledgeSources,
            ContextBoundary boundary,
            boolean approved) {
        return new ExpertAgentDefinition(
                null,
                "owner-scope-a",
                name,
                purpose,
                "text",
                "text",
                owners,
                tools,
                skills,
                knowledgeSources,
                boundary,
                "user-a",
                approved,
                true,
                "v1",
                null);
    }

    private static OwnerPrincipal principal(Set<String> owners) {
        return new OwnerPrincipal("owner-scope-a", "user-a", IdentityProviderType.INTERNAL, owners, Set.of());
    }

    private static OrchestrationService serviceWith(
            AgentRuntime runtime,
            OrchestrationTraceStore traceStore) {
        ExpertAgentRegistry registry = new ExpertAgentRegistry();
        RuntimeContextFactory contextFactory = new RuntimeContextFactory();
        ToolService toolService = new ToolService(new InMemoryToolStore(), java.util.List.of(
                new AgentAsToolExecutor(registry, runtime, contextFactory, traceStore)));
        return new OrchestrationService(
                registry,
                new SupervisorRouter(),
                runtime,
                contextFactory,
                toolService,
                RuntimeTelemetry.noop(),
                new SensitiveDataRedactor(),
                traceStore);
    }

    private static boolean eventuallyHasBackgroundCompletion(
            OrchestrationService service,
            String agentId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            boolean found = service.listTraces(principal(Set.of("user-a"))).stream()
                    .anyMatch(trace -> trace.selectedAgentId().equals(agentId)
                            && trace.status() == OrchestrationStatus.BACKGROUND_COMPLETED
                            && trace.steps().stream().anyMatch(step -> step.action().equals("background_complete")));
            if (found) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    private static class RecordingAgentRuntime implements AgentRuntime {

        private final java.util.List<AgentRunRequest> requests = new java.util.ArrayList<>();
        private final java.util.Map<String, String> replies = new java.util.HashMap<>();
        private final java.util.Set<String> failAlwaysForAgentIds = new java.util.HashSet<>();
        private String failOnceForAgentId;

        @Override
        public Mono<AgentReply> complete(AgentRunRequest request) {
            requests.add(request);
            if (failAlwaysForAgentIds.contains(request.context().agentId())) {
                return Mono.error(new IllegalStateException("agent failed"));
            }
            if (request.context().agentId().equals(failOnceForAgentId)) {
                failOnceForAgentId = null;
                return Mono.error(new IllegalStateException("child failed"));
            }
            return Mono.just(new AgentReply(replies.getOrDefault(request.context().agentId(), "child-answer")));
        }

        @Override
        public Flux<AgentRuntimeEvent> stream(AgentRunRequest request) {
            return Flux.empty();
        }

        private void failOnceFor(String agentId) {
            this.failOnceForAgentId = agentId;
        }

        private void failAlwaysFor(String agentId) {
            this.failAlwaysForAgentIds.add(agentId);
        }

        private void reply(String agentId, String content) {
            replies.put(agentId, content);
        }
    }

    private static class BlockingAgentRuntime implements AgentRuntime {

        private final java.util.List<AgentRunRequest> requests = new java.util.ArrayList<>();
        private final CountDownLatch childStarted = new CountDownLatch(1);
        private final CountDownLatch readinessChild = new CountDownLatch(1);

        @Override
        public Mono<AgentReply> complete(AgentRunRequest request) {
            requests.add(request);
            childStarted.countDown();
            return Mono.fromCallable(() -> {
                if (!readinessChild.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("child was not readinessd");
                }
                return new AgentReply("background-answer");
            });
        }

        @Override
        public Flux<AgentRuntimeEvent> stream(AgentRunRequest request) {
            return Flux.empty();
        }

        private void readiness() {
            readinessChild.countDown();
        }
    }
}
