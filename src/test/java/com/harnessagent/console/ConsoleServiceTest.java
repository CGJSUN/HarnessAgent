package com.harnessagent.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.console.application.ActivitySearchFilter;
import com.harnessagent.console.application.ConsoleService;
import com.harnessagent.console.view.AgentManagementView;
import com.harnessagent.console.view.CostUsageReport;
import com.harnessagent.console.view.OperationalMetricSummary;
import com.harnessagent.console.view.ToolConfirmationView;
import com.harnessagent.console.view.UserConsoleView;
import com.harnessagent.production.infrastructure.InMemoryRuntimeTelemetry;
import com.harnessagent.production.telemetry.TelemetryEventType;
import com.harnessagent.rag.persistence.InMemoryKnowledgeStore;
import com.harnessagent.rag.application.KnowledgeRetrievalPolicy;
import com.harnessagent.rag.application.KnowledgeService;
import com.harnessagent.rag.application.TextChunker;
import com.harnessagent.rag.application.TextTokenizer;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.security.application.AuthorizationService;
import com.harnessagent.security.domain.IdentityProviderType;
import com.harnessagent.security.application.SecurityActivityService;
import com.harnessagent.security.domain.OwnerPrincipal;
import com.harnessagent.security.domain.Permission;
import com.harnessagent.security.domain.ResourceAccessPolicy;
import com.harnessagent.security.domain.ResourceType;
import com.harnessagent.security.application.SensitiveDataRedactor;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.persistence.InMemorySessionStore;
import com.harnessagent.tooling.persistence.InMemoryToolStore;
import com.harnessagent.tooling.domain.ToolActivityPolicy;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolParameterSchema;
import com.harnessagent.tooling.domain.ToolPermissionPolicy;
import com.harnessagent.tooling.domain.ToolRegistration;
import com.harnessagent.tooling.domain.ToolRiskLevel;
import com.harnessagent.tooling.application.ToolService;
import com.harnessagent.tooling.domain.ToolSourceType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.harnessagent.tooling.execution.ToolExecutionCommand;

class ConsoleServiceTest {

    private final RuntimeContextFactory contextFactory = new RuntimeContextFactory();
    private final InMemorySessionStore sessionStore = new InMemorySessionStore();
    private final HarnessAgentProperties properties = properties();
    private final ToolService toolService = new ToolService(new InMemoryToolStore(), List.of());
    private final KnowledgeService knowledgeService = new KnowledgeService(
            new InMemoryKnowledgeStore(),
            new TextChunker(),
            new TextTokenizer(),
            new KnowledgeRetrievalPolicy());
    private final InMemoryRuntimeTelemetry telemetry = new InMemoryRuntimeTelemetry(true);
    private final SecurityActivityService securityActivityService = new SecurityActivityService(
            new SensitiveDataRedactor(),
            new AuthorizationService());
    private final ConsoleService service = new ConsoleService(
            contextFactory,
            sessionStore,
            properties,
            toolService,
            knowledgeService,
            telemetry,
            securityActivityService);

    @Test
    void userConsoleShowsHistoryForCurrentUserAndAgent() {
        sessionStore.appendMessage(
                contextFactory.create("owner-scope-a", "user-a", "agent-a", "session-a"),
                ChatMessage.user("hello"));

        UserConsoleView view = service.userConsole(user(), "agent-a", "session-a");

        assertThat(view.sessions()).hasSize(1);
        assertThat(view.messages()).extracting(ChatMessage::content).containsExactly("hello");
    }

    @Test
    void ownerCanManageAgentPromptAndActivityChange() {
        AgentManagementView view = service.updateAgentPrompt(user(), "agent-a", "新的系统提示词");

        assertThat(view.systemPrompt()).isEqualTo("新的系统提示词");
        assertThat(securityActivityService.search(user(), "owner-scope-a", activityPolicy())).hasSize(1);
    }

    @Test
    void ownerCanListPersonalDiagnosticAgents() {
        assertThat(service.listAgents(user()))
                .extracting(AgentManagementView::agentId)
                .containsExactly("agent-a");
    }

    @Test
    void ownerCanReadMetricsAndCostReports() {
        telemetry.record(
                TelemetryEventType.TOKEN,
                "owner-scope-a",
                "user-a",
                "agent-a",
                "budget",
                Duration.ofMillis(3),
                Map.of("usedTokens", 42, "providerId", "dashscope"));

        OperationalMetricSummary metrics = service.metrics(user());
        CostUsageReport cost = service.cost(user(), "agent-a", "dashscope");

        assertThat(metrics.totalDurationMillis()).isEqualTo(3);
        assertThat(cost.estimatedTokens()).isEqualTo(42);
    }

    @Test
    void ownerCanSearchOwnActivitysWithFilters() {
        securityActivityService.record(
                user(),
                ResourceType.AGENT,
                "agent-a",
                "UPDATE_AGENT_PROMPT",
                Map.of("agentId", "agent-a"));

        assertThat(service.activitySearch(
                user(),
                new ActivitySearchFilter(null, null, "agent-a", "UPDATE_AGENT_PROMPT", null, null)).securityActivity())
                .hasSize(1);
    }

    @Test
    void ownerCanDisableToolAndLegacySkillGovernanceIsDisabled() {
        ToolDefinition tool = toolService.registerTool(new ToolRegistration(
                "owner-scope-a",
                "crm.lookup",
                "lookup",
                "CRM",
                "owner-a",
                ToolSourceType.INTERNAL,
                "crm",
                ToolRiskLevel.READ_ONLY,
                false,
                true,
                ToolParameterSchema.empty(),
                ToolPermissionPolicy.allowAll(),
                ToolActivityPolicy.standard()));

        ToolDefinition disabled = service.setToolEnabled(user(), tool.id(), false);

        assertThat(disabled.enabled()).isFalse();
    }

    @Test
    void userConsoleIncludesPendingToolOperationContext() {
        ToolDefinition tool = toolService.registerTool(new ToolRegistration(
                "owner-scope-a",
                "ticket.update",
                "update ticket",
                "ServiceDesk",
                "owner-a",
                ToolSourceType.INTERNAL,
                "service-desk",
                ToolRiskLevel.HIGH_RISK,
                true,
                true,
                new ToolParameterSchema(Set.of("ticketId"), Set.of(), Map.of(), Set.of()),
                ToolPermissionPolicy.allowAll(),
                ToolActivityPolicy.standard()));
        toolService.execute(new ToolExecutionCommand(
                "owner-scope-a",
                "user-a",
                "agent-a",
                "session-a",
                tool.id(),
                Map.of("ticketId", "T-1"),
                Set.of(),
                Set.of(),
                false,
                null,
                null,
                "idem-1"));

        UserConsoleView view = service.userConsole(user(), "agent-a", "session-a");

        assertThat(view.confirmationPrompts()).hasSize(1);
        ToolConfirmationView prompt = view.confirmationPrompts().get(0);
        assertThat(prompt.confirmationId()).isNotBlank();
        assertThat(prompt.status()).isEqualTo("PENDING");
        assertThat(prompt.idempotencyKey()).isEqualTo("idem-1");
        assertThat(prompt.operationSummary()).containsEntry("toolName", "ticket.update");
        assertThat(prompt.operationSummary()).containsKey("parameters");
    }

    private static HarnessAgentProperties properties() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setName("agent-a");
        agent.setSystemPrompt("默认提示词");
        agent.setModelProvider("echo");
        agent.setModelName("echo-local");
        agent.setWorkspace(".harness-agent/workspaces/agent-a");
        properties.getAgents().put("agent-a", agent);
        return properties;
    }

    private static OwnerPrincipal user() {
        return principal("user-a", Set.of());
    }

    private static OwnerPrincipal principal(String userId, Set<String> owners) {
        return new OwnerPrincipal("owner-scope-a", userId, IdentityProviderType.INTERNAL, owners, Set.of());
    }

    private static ResourceAccessPolicy activityPolicy() {
        return ResourceAccessPolicy.ownerOnly(
                "owner-scope-a",
                "user-a",
                ResourceType.ACTIVITY,
                Permission.SEARCH_ACTIVITY);
    }
}
