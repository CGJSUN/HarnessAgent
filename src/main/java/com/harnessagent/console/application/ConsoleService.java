package com.harnessagent.console.application;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.console.view.AgentManagementView;
import com.harnessagent.console.view.ConsoleAuditResult;
import com.harnessagent.console.view.CostUsageReport;
import com.harnessagent.console.view.KnowledgeSourceView;
import com.harnessagent.console.view.OperationalMetricSummary;
import com.harnessagent.console.view.ToolConfirmationView;
import com.harnessagent.console.view.ToolStatusView;
import com.harnessagent.console.view.UserConsoleView;
import com.harnessagent.production.telemetry.RuntimeTelemetry;
import com.harnessagent.production.telemetry.TelemetryEvent;
import com.harnessagent.production.telemetry.TelemetryEventType;
import com.harnessagent.rag.application.KnowledgeService;
import com.harnessagent.rag.domain.RagMetric;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.security.domain.Permission;
import com.harnessagent.security.domain.ResourceAccessPolicy;
import com.harnessagent.security.domain.ResourceType;
import com.harnessagent.security.persistence.SecurityAuditRecord;
import com.harnessagent.security.application.SecurityAuditService;
import com.harnessagent.security.domain.SecurityPrincipal;
import com.harnessagent.session.persistence.SessionStore;
import com.harnessagent.tooling.audit.ToolAuditRecord;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolPendingConfirmation;
import com.harnessagent.tooling.application.ToolService;
import com.harnessagent.rag.domain.KnowledgeSource;
import com.harnessagent.security.application.SkillGovernanceService;
import com.harnessagent.security.domain.SkillVersion;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ConsoleService {

    private final RuntimeContextFactory runtimeContextFactory;
    private final SessionStore sessionStore;
    private final HarnessAgentProperties properties;
    private final ToolService toolService;
    private final KnowledgeService knowledgeService;
    private final SkillGovernanceService skillGovernanceService;
    private final RuntimeTelemetry telemetry;
    private final SecurityAuditService securityAuditService;

    public ConsoleService(
            RuntimeContextFactory runtimeContextFactory,
            SessionStore sessionStore,
            HarnessAgentProperties properties,
            ToolService toolService,
            KnowledgeService knowledgeService,
            SkillGovernanceService skillGovernanceService,
            RuntimeTelemetry telemetry,
            SecurityAuditService securityAuditService) {
        this.runtimeContextFactory = runtimeContextFactory;
        this.sessionStore = sessionStore;
        this.properties = properties;
        this.toolService = toolService;
        this.knowledgeService = knowledgeService;
        this.skillGovernanceService = skillGovernanceService;
        this.telemetry = telemetry;
        this.securityAuditService = securityAuditService;
    }

    public UserConsoleView userConsole(
            SecurityPrincipal principal, String agentId, String sessionId) {
        RuntimeContextScope context = runtimeContextFactory.create(
                principal.tenantId(),
                principal.userId(),
                agentId,
                sessionId == null || sessionId.isBlank() ? "_" : sessionId);
        List<ToolAuditRecord> toolAudit = toolService.listAudit(principal.tenantId()).stream()
                .filter(record -> record.userId().equals(principal.userId()))
                .filter(record -> record.agentId().equals(agentId))
                .toList();
        List<ToolStatusView> toolStatus = toolAudit.stream()
                .map(record -> new ToolStatusView(
                        record.toolId(),
                        record.toolName(),
                        record.status(),
                        record.sessionId(),
                        record.durationMillis()))
                .toList();
        List<ToolConfirmationView> confirmations = toolService.listPendingConfirmations(
                        principal.tenantId(),
                        principal.userId(),
                        agentId,
                        context.sessionId()).stream()
                .map(ConsoleService::confirmationView)
                .toList();
        return new UserConsoleView(
                sessionStore.listSessions(context.tenantId(), context.userId(), context.agentId()),
                "_".equals(context.sessionId()) ? List.of() : sessionStore.listMessages(context),
                List.of(),
                toolStatus,
                confirmations,
                List.of());
    }

    private static ToolConfirmationView confirmationView(ToolPendingConfirmation pending) {
        return new ToolConfirmationView(
                pending.confirmationId(),
                pending.toolId(),
                pending.toolName(),
                pending.sessionId(),
                pending.riskLevel().name(),
                pending.status().name(),
                pending.sanitizedInput(),
                pending.operationSummary(),
                pending.idempotencyKey());
    }

    public List<AgentManagementView> listAgents(SecurityPrincipal principal) {
        requireAdmin(principal);
        return properties.getAgents().entrySet().stream()
                .map(entry -> AgentManagementView.from(entry.getKey(), entry.getValue()))
                .toList();
    }

    public AgentManagementView updateAgentPrompt(
            SecurityPrincipal principal, String agentId, String systemPrompt) {
        requireAdmin(principal);
        HarnessAgentProperties.AgentDefinition definition = properties.requireAgent(agentId);
        String before = definition.getSystemPrompt() == null ? "" : definition.getSystemPrompt();
        definition.setSystemPrompt(systemPrompt);
        securityAuditService.record(
                principal,
                ResourceType.AGENT,
                agentId,
                "UPDATE_AGENT_PROMPT",
                Map.of(
                        "agentId", agentId,
                        "beforeLength", before.length(),
                        "afterLength", systemPrompt == null ? 0 : systemPrompt.length()));
        return AgentManagementView.from(agentId, definition);
    }

    public AgentManagementView updateAgentConfig(
            SecurityPrincipal principal,
            String agentId,
            String modelProvider,
            String modelName,
            String workspace,
            Boolean compaction,
            Integer maxIters) {
        requireAdmin(principal);
        HarnessAgentProperties.AgentDefinition definition = properties.requireAgent(agentId);
        if (modelProvider != null) {
            definition.setModelProvider(modelProvider);
        }
        if (modelName != null) {
            definition.setModelName(modelName);
        }
        if (workspace != null) {
            definition.setWorkspace(workspace);
        }
        if (compaction != null) {
            definition.setCompaction(compaction);
        }
        if (maxIters != null) {
            definition.setMaxIters(maxIters);
        }
        securityAuditService.record(
                principal,
                ResourceType.AGENT,
                agentId,
                "UPDATE_AGENT_CONFIG",
                Map.of("agentId", agentId));
        return AgentManagementView.from(agentId, definition);
    }

    public List<ToolDefinition> listTools(SecurityPrincipal principal) {
        requireAdmin(principal);
        return toolService.listTools(principal.tenantId());
    }

    public ToolDefinition setToolEnabled(SecurityPrincipal principal, String toolId, boolean enabled) {
        requireAdmin(principal);
        ToolDefinition updated = toolService.setEnabled(toolId, enabled);
        securityAuditService.record(
                principal,
                ResourceType.TOOL,
                toolId,
                enabled ? "ENABLE_TOOL" : "DISABLE_TOOL",
                Map.of("toolId", toolId));
        return updated;
    }

    public List<KnowledgeSourceView> listKnowledge(SecurityPrincipal principal) {
        requireAdmin(principal);
        return knowledgeService.listSources(principal.tenantId()).stream()
                .map(KnowledgeSourceView::from)
                .toList();
    }

    public KnowledgeSource deleteKnowledge(SecurityPrincipal principal, String sourceId) {
        requireAdmin(principal);
        KnowledgeSource deleted = knowledgeService.deleteSource(sourceId);
        securityAuditService.record(
                principal,
                ResourceType.KNOWLEDGE_SOURCE,
                sourceId,
                "DELETE_KNOWLEDGE_SOURCE",
                Map.of("sourceId", sourceId));
        return deleted;
    }

    public KnowledgeSource revokeKnowledge(SecurityPrincipal principal, String sourceId) {
        requireAdmin(principal);
        KnowledgeSource revoked = knowledgeService.revokeSource(sourceId);
        securityAuditService.record(
                principal,
                ResourceType.KNOWLEDGE_SOURCE,
                sourceId,
                "REVOKE_KNOWLEDGE_SOURCE",
                Map.of("sourceId", sourceId));
        return revoked;
    }

    public List<SkillVersion> listSkills(SecurityPrincipal principal, String skillName) {
        requireAdmin(principal);
        return skillGovernanceService.list(principal.tenantId(), skillName);
    }

    public SkillVersion approveSkill(SecurityPrincipal principal, String versionId) {
        requireAdmin(principal);
        SkillVersion skill = skillGovernanceService.approve(versionId, principal.userId());
        auditSkill(principal, skill, "APPROVE_SKILL");
        return skill;
    }

    public SkillVersion publishSkill(SecurityPrincipal principal, String versionId) {
        requireAdmin(principal);
        SkillVersion skill = skillGovernanceService.publish(versionId);
        auditSkill(principal, skill, "PUBLISH_SKILL");
        return skill;
    }

    public SkillVersion disableSkill(SecurityPrincipal principal, String versionId) {
        requireAdmin(principal);
        SkillVersion skill = skillGovernanceService.disable(versionId);
        auditSkill(principal, skill, "DISABLE_SKILL");
        return skill;
    }

    public OperationalMetricSummary metrics(SecurityPrincipal principal) {
        requireAdminOrOps(principal);
        List<TelemetryEvent> events = telemetry.list(principal.tenantId());
        List<RagMetric> ragMetrics = knowledgeService.listMetrics(principal.tenantId());
        long failures = events.stream()
                .filter(event -> String.valueOf(event.attributes().get("status")).contains("failed"))
                .count();
        long toolCalls = toolService.listAudit(principal.tenantId()).size();
        return new OperationalMetricSummary(
                sessionStore.listSessions(principal.tenantId(), principal.userId(), null).size(),
                events.stream().filter(event -> event.type() == TelemetryEventType.AGENT
                        || event.type() == TelemetryEventType.MODEL).count(),
                toolCalls,
                ragMetrics.stream().filter(RagMetric::hit).count(),
                ragMetrics.stream().filter(metric -> !metric.hit()).count(),
                failures,
                events.stream().mapToLong(TelemetryEvent::durationMillis).sum(),
                knowledgeService.listFeedback(principal.tenantId()).size());
    }

    public CostUsageReport cost(SecurityPrincipal principal, String agentId, String providerId) {
        requireAdminOrOps(principal);
        List<TelemetryEvent> tokenEvents = telemetry.list(principal.tenantId()).stream()
                .filter(event -> event.type() == TelemetryEventType.TOKEN)
                .filter(event -> agentId == null || agentId.isBlank() || event.agentId().equals(agentId))
                .filter(event -> providerId == null || providerId.isBlank()
                        || providerId.equals(String.valueOf(event.attributes().get("providerId"))))
                .toList();
        long estimatedTokens = tokenEvents.stream()
                .map(event -> event.attributes().get("usedTokens"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToLong(Number::longValue)
                .sum();
        return new CostUsageReport(
                principal.tenantId(),
                agentId == null ? "" : agentId,
                providerId == null ? "" : providerId,
                tokenEvents.size(),
                estimatedTokens,
                estimatedTokens * 0.000001d);
    }

    public ConsoleAuditResult auditSearch(SecurityPrincipal principal) {
        return auditSearch(principal, new AuditSearchFilter(null, null, null, null, null, null));
    }

    public ConsoleAuditResult auditSearch(SecurityPrincipal principal, AuditSearchFilter filter) {
        ResourceAccessPolicy policy = new ResourceAccessPolicy(
                ResourceType.AUDIT,
                principal.tenantId(),
                Set.of(),
                Set.of("auditor", "admin"),
                Set.of(),
                Set.of(Permission.SEARCH_AUDIT));
        List<SecurityAuditRecord> securityAudit =
                securityAuditService.search(principal, principal.tenantId(), policy).stream()
                        .filter(record -> filter.matches(
                                record.userId(),
                                "",
                                record.resourceId(),
                                record.action(),
                                record.occurredAt()))
                        .toList();
        return new ConsoleAuditResult(
                toolService.listAudit(principal.tenantId()).stream()
                        .filter(record -> filter.matches(
                                record.userId(),
                                record.sessionId(),
                                record.toolId(),
                                record.status().name(),
                                record.occurredAt()))
                        .toList(),
                securityAudit);
    }

    private void auditSkill(SecurityPrincipal principal, SkillVersion skill, String action) {
        securityAuditService.record(
                principal,
                ResourceType.SKILL,
                skill.id(),
                action,
                Map.of("skillName", skill.skillName(), "version", skill.version()));
    }

    private static void requireAdmin(SecurityPrincipal principal) {
        if (!principal.roles().contains("admin")) {
            throw new IllegalStateException("admin role is required");
        }
    }

    private static void requireAdminOrOps(SecurityPrincipal principal) {
        if (!principal.roles().contains("admin") && !principal.roles().contains("ops")) {
            throw new IllegalStateException("admin or ops role is required");
        }
    }
}
