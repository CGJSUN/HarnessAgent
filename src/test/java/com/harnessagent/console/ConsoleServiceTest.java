package com.harnessagent.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.production.InMemoryRuntimeTelemetry;
import com.harnessagent.production.TelemetryEventType;
import com.harnessagent.rag.InMemoryKnowledgeStore;
import com.harnessagent.rag.KnowledgeRetrievalPolicy;
import com.harnessagent.rag.KnowledgeService;
import com.harnessagent.rag.TextChunker;
import com.harnessagent.rag.TextTokenizer;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.security.AuthorizationService;
import com.harnessagent.security.IdentityProviderType;
import com.harnessagent.security.SecurityAuditService;
import com.harnessagent.security.SecurityPrincipal;
import com.harnessagent.security.SensitiveDataRedactor;
import com.harnessagent.security.SkillGovernanceService;
import com.harnessagent.security.SkillVersion;
import com.harnessagent.session.ChatMessage;
import com.harnessagent.session.InMemorySessionStore;
import com.harnessagent.tooling.InMemoryToolStore;
import com.harnessagent.tooling.ToolAuditPolicy;
import com.harnessagent.tooling.ToolDefinition;
import com.harnessagent.tooling.ToolParameterSchema;
import com.harnessagent.tooling.ToolPermissionPolicy;
import com.harnessagent.tooling.ToolRegistration;
import com.harnessagent.tooling.ToolRiskLevel;
import com.harnessagent.tooling.ToolService;
import com.harnessagent.tooling.ToolSourceType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

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
    private final SkillGovernanceService skillGovernanceService = new SkillGovernanceService();
    private final InMemoryRuntimeTelemetry telemetry = new InMemoryRuntimeTelemetry(true);
    private final SecurityAuditService securityAuditService = new SecurityAuditService(
            new SensitiveDataRedactor(),
            new AuthorizationService());
    private final ConsoleService service = new ConsoleService(
            contextFactory,
            sessionStore,
            properties,
            toolService,
            knowledgeService,
            skillGovernanceService,
            telemetry,
            securityAuditService);

    @Test
    void userConsoleShowsHistoryForCurrentUserAndAgent() {
        sessionStore.appendMessage(
                contextFactory.create("tenant-a", "user-a", "agent-a", "session-a"),
                ChatMessage.user("hello"));

        UserConsoleView view = service.userConsole(user(), "agent-a", "session-a");

        assertThat(view.sessions()).hasSize(1);
        assertThat(view.messages()).extracting(ChatMessage::content).containsExactly("hello");
    }

    @Test
    void adminCanManageAgentPromptAndAuditChange() {
        AgentManagementView view = service.updateAgentPrompt(admin(), "agent-a", "新的系统提示词");

        assertThat(view.systemPrompt()).isEqualTo("新的系统提示词");
        assertThat(securityAuditService.search(admin(), "tenant-a", auditPolicy())).hasSize(1);
    }

    @Test
    void nonAdminCannotAccessManagementViews() {
        assertThatThrownBy(() -> service.listAgents(user()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin");
    }

    @Test
    void opsCanReadMetricsAndCostReports() {
        telemetry.record(
                TelemetryEventType.TOKEN,
                "tenant-a",
                "user-a",
                "agent-a",
                "budget",
                Duration.ofMillis(3),
                Map.of("usedTokens", 42, "providerId", "dashscope"));

        OperationalMetricSummary metrics = service.metrics(ops());
        CostUsageReport cost = service.cost(ops(), "agent-a", "dashscope");

        assertThat(metrics.totalDurationMillis()).isEqualTo(3);
        assertThat(cost.estimatedTokens()).isEqualTo(42);
    }

    @Test
    void auditorCanSearchAuditsWithFilters() {
        securityAuditService.record(
                admin(),
                com.harnessagent.security.ResourceType.AGENT,
                "agent-a",
                "UPDATE_AGENT_PROMPT",
                Map.of("agentId", "agent-a"));

        assertThat(service.auditSearch(
                auditor(),
                new AuditSearchFilter(null, null, "agent-a", "UPDATE_AGENT_PROMPT", null, null)).securityAudit())
                .hasSize(1);
        assertThatThrownBy(() -> service.auditSearch(user()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void adminCanDisableToolAndManageSkillLifecycle() {
        ToolDefinition tool = toolService.registerTool(new ToolRegistration(
                "tenant-a",
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
                ToolAuditPolicy.standard()));
        SkillVersion proposed = skillGovernanceService.propose(
                "tenant-a", "finance-helper", "1.0.0", "git://skills/finance", "owner-a");

        ToolDefinition disabled = service.setToolEnabled(admin(), tool.id(), false);
        SkillVersion approved = service.approveSkill(admin(), proposed.id());
        SkillVersion published = service.publishSkill(admin(), approved.id());
        SkillVersion disabledSkill = service.disableSkill(admin(), published.id());

        assertThat(disabled.enabled()).isFalse();
        assertThat(disabledSkill.status().name()).isEqualTo("DISABLED");
    }

    @Test
    void userConsoleIncludesPendingToolOperationContext() {
        ToolDefinition tool = toolService.registerTool(new ToolRegistration(
                "tenant-a",
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
                ToolAuditPolicy.standard()));
        toolService.execute(new com.harnessagent.tooling.ToolExecutionCommand(
                "tenant-a",
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

    private static SecurityPrincipal user() {
        return principal("user-a", Set.of("employee"));
    }

    private static SecurityPrincipal admin() {
        return principal("admin-a", Set.of("admin"));
    }

    private static SecurityPrincipal ops() {
        return principal("ops-a", Set.of("ops"));
    }

    private static SecurityPrincipal auditor() {
        return principal("auditor-a", Set.of("auditor"));
    }

    private static SecurityPrincipal principal(String userId, Set<String> roles) {
        return new SecurityPrincipal("tenant-a", userId, IdentityProviderType.INTERNAL, roles, Set.of());
    }

    private static com.harnessagent.security.ResourceAccessPolicy auditPolicy() {
        return new com.harnessagent.security.ResourceAccessPolicy(
                com.harnessagent.security.ResourceType.AUDIT,
                "tenant-a",
                Set.of(),
                Set.of("admin"),
                Set.of(),
                Set.of(com.harnessagent.security.Permission.SEARCH_AUDIT));
    }
}
