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
import com.harnessagent.security.domain.SecurityPrincipal;
import com.harnessagent.security.application.SensitiveDataRedactor;
import com.harnessagent.tooling.persistence.InMemoryToolStore;
import com.harnessagent.tooling.application.ToolService;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.harnessagent.orchestration.application.ExpertAgentRegistry;
import com.harnessagent.orchestration.application.OrchestrationService;
import com.harnessagent.orchestration.application.SupervisorRouter;
import com.harnessagent.orchestration.domain.AgentToolDefinition;
import com.harnessagent.orchestration.domain.ExpertAgentDefinition;
import com.harnessagent.orchestration.domain.OrchestrationRequest;
import com.harnessagent.orchestration.domain.OrchestrationResult;
import com.harnessagent.orchestration.domain.OrchestrationStatus;

class OrchestrationServiceTest {

    private final ExpertAgentRegistry registry = new ExpertAgentRegistry();
    private final RecordingAgentRuntime agentRuntime = new RecordingAgentRuntime();
    private final ToolService toolService = new ToolService(new InMemoryToolStore(), java.util.List.of());
    private final OrchestrationService service = new OrchestrationService(
            registry,
            new SupervisorRouter(),
            agentRuntime,
            new RuntimeContextFactory(),
            toolService,
            RuntimeTelemetry.noop(),
            new SensitiveDataRedactor());

    @Test
    void registersExpertAgentAndRoutesByIntentAndPermission() {
        ExpertAgentDefinition agent = service.register(agent(
                "knowledge-agent",
                "回答制度知识问题",
                Set.of("employee"),
                true));

        OrchestrationResult result = service.orchestrate(new OrchestrationRequest(
                principal(Set.of("employee")),
                "supervisor",
                "制度知识",
                "查询报销制度",
                Map.of("question", "报销制度", "secret", "不应共享")));

        assertThat(result.decision().selectedAgentId()).isEqualTo(agent.id());
        assertThat(result.trace().steps()).hasSize(1);
        assertThat(agentRuntime.requests).hasSize(1);
        assertThat(result.trace().handoffs().get(0).sharedContext())
                .containsKey("question")
                .doesNotContainKey("secret");
    }

    @Test
    void exposesApprovedChildAgentAsTool() {
        ExpertAgentDefinition agent = service.register(agent(
                "approval-agent",
                "处理审批申请",
                Set.of("employee"),
                true));

        AgentToolDefinition tool = service.asTool(agent.id());

        assertThat(tool.toolName()).isEqualTo("agent.approval-agent");
        assertThat(tool.requiredRoles()).contains("employee");
        assertThat(toolService.listTools("tenant-a")).hasSize(1);
    }

    @Test
    void rejectsUnapprovedChildAgentAsTool() {
        ExpertAgentDefinition agent = service.register(agent(
                "draft-agent",
                "草稿 Agent",
                Set.of("employee"),
                false));

        assertThatThrownBy(() -> service.asTool(agent.id()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void escalatesWhenNoPermittedAgentCanHandleTask() {
        service.register(agent("finance-agent", "财务专用", Set.of("finance"), true));

        OrchestrationResult result = service.orchestrate(new OrchestrationRequest(
                principal(Set.of("employee")),
                "supervisor",
                "财务",
                "处理薪资",
                Map.of("question", "薪资")));

        assertThat(result.decision().status()).isEqualTo(OrchestrationStatus.ESCALATED);
        assertThat(result.trace().status()).isEqualTo(OrchestrationStatus.ESCALATED);
    }

    @Test
    void traceSearchRequiresElevatedRole() {
        assertThatThrownBy(() -> service.listTraces(principal(Set.of("employee"))))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ExpertAgentDefinition agent(
            String name,
            String purpose,
            Set<String> roles,
            boolean approved) {
        return new ExpertAgentDefinition(
                null,
                "tenant-a",
                name,
                purpose,
                "text",
                "text",
                roles,
                Set.of("tool-a"),
                Set.of("knowledge-a"),
                "owner-a",
                approved,
                true,
                "v1",
                null);
    }

    private static SecurityPrincipal principal(Set<String> roles) {
        return new SecurityPrincipal("tenant-a", "user-a", IdentityProviderType.INTERNAL, roles, Set.of());
    }

    private static class RecordingAgentRuntime implements AgentRuntime {

        private final java.util.List<AgentRunRequest> requests = new java.util.ArrayList<>();

        @Override
        public Mono<AgentReply> complete(AgentRunRequest request) {
            requests.add(request);
            return Mono.just(new AgentReply("child-answer"));
        }

        @Override
        public Flux<AgentRuntimeEvent> stream(AgentRunRequest request) {
            return Flux.empty();
        }
    }
}
