package com.harnessagent.console.application;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.console.view.AgentManagementView;
import com.harnessagent.console.view.ConsoleActivityResult;
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
import com.harnessagent.security.persistence.SecurityActivityRecord;
import com.harnessagent.security.application.SecurityActivityService;
import com.harnessagent.security.domain.OwnerPrincipal;
import com.harnessagent.session.persistence.SessionStore;
import com.harnessagent.skill.domain.PersonalSkillMetadata;
import com.harnessagent.tooling.activity.ToolActivityRecord;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolPendingConfirmation;
import com.harnessagent.tooling.application.ToolService;
import com.harnessagent.rag.domain.KnowledgeSource;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ConsoleService {

    private final RuntimeContextFactory runtimeContextFactory;
    private final SessionStore sessionStore;
    private final HarnessAgentProperties properties;
    private final ToolService toolService;
    private final KnowledgeService knowledgeService;
    private final RuntimeTelemetry telemetry;
    private final SecurityActivityService securityActivityService;

    public ConsoleService(
            RuntimeContextFactory runtimeContextFactory,
            SessionStore sessionStore,
            HarnessAgentProperties properties,
            ToolService toolService,
            KnowledgeService knowledgeService,
            RuntimeTelemetry telemetry,
            SecurityActivityService securityActivityService) {
        this.runtimeContextFactory = runtimeContextFactory;
        this.sessionStore = sessionStore;
        this.properties = properties;
        this.toolService = toolService;
        this.knowledgeService = knowledgeService;
        this.telemetry = telemetry;
        this.securityActivityService = securityActivityService;
    }

    public UserConsoleView userConsole(
            OwnerPrincipal principal, String agentId, String sessionId) {
        RuntimeContextScope context = runtimeContextFactory.createFromOwnerScope(
                principal.scopeId(),
                principal.ownerId(),
                agentId,
                sessionId == null || sessionId.isBlank() ? "_" : sessionId);
        List<ToolActivityRecord> toolActivity = toolService.listActivity(principal.scopeId()).stream()
                .filter(record -> record.ownerId().equals(principal.ownerId()))
                .filter(record -> record.agentId().equals(agentId))
                .toList();
        List<ToolStatusView> toolStatus = toolActivity.stream()
                .map(record -> new ToolStatusView(
                        record.toolId(),
                        record.toolName(),
                        record.status(),
                        record.sessionId(),
                        record.durationMillis()))
                .toList();
        List<ToolConfirmationView> confirmations = toolService.listPendingConfirmations(
                        principal.scopeId(),
                        principal.ownerId(),
                        agentId,
                        context.sessionId()).stream()
                .map(ConsoleService::confirmationView)
                .toList();
        return new UserConsoleView(
                sessionStore.listSessions(context.ownerScopeId(), context.ownerId(), context.agentId()),
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

    public List<AgentManagementView> listAgents(OwnerPrincipal principal) {
        requireOwner(principal);
        return properties.getAgents().entrySet().stream()
                .map(entry -> AgentManagementView.from(entry.getKey(), entry.getValue()))
                .toList();
    }

    public AgentManagementView updateAgentPrompt(
            OwnerPrincipal principal, String agentId, String systemPrompt) {
        requireOwner(principal);
        HarnessAgentProperties.AgentDefinition definition = properties.requireAgent(agentId);
        String before = definition.getSystemPrompt() == null ? "" : definition.getSystemPrompt();
        definition.setSystemPrompt(systemPrompt);
        securityActivityService.record(
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
            OwnerPrincipal principal,
            String agentId,
            String modelProvider,
            String modelName,
            String workspace,
            Boolean compaction,
            Integer maxIters) {
        requireOwner(principal);
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
        securityActivityService.record(
                principal,
                ResourceType.AGENT,
                agentId,
                "UPDATE_AGENT_CONFIG",
                Map.of("agentId", agentId));
        return AgentManagementView.from(agentId, definition);
    }

    public List<ToolDefinition> listTools(OwnerPrincipal principal) {
        requireOwner(principal);
        return toolService.listTools(principal.scopeId());
    }

    public ToolDefinition setToolEnabled(OwnerPrincipal principal, String toolId, boolean enabled) {
        requireOwner(principal);
        ToolDefinition updated = toolService.setEnabled(toolId, enabled);
        securityActivityService.record(
                principal,
                ResourceType.TOOL,
                toolId,
                enabled ? "ENABLE_TOOL" : "DISABLE_TOOL",
                Map.of("toolId", toolId));
        return updated;
    }

    public List<KnowledgeSourceView> listKnowledge(OwnerPrincipal principal) {
        requireOwner(principal);
        return knowledgeService.listSources(principal.scopeId()).stream()
                .map(KnowledgeSourceView::from)
                .toList();
    }

    public KnowledgeSource deleteKnowledge(OwnerPrincipal principal, String sourceId) {
        requireOwner(principal);
        KnowledgeSource deleted = knowledgeService.deleteSource(sourceId);
        securityActivityService.record(
                principal,
                ResourceType.KNOWLEDGE_SOURCE,
                sourceId,
                "DELETE_KNOWLEDGE_SOURCE",
                Map.of("sourceId", sourceId));
        return deleted;
    }

    public KnowledgeSource revokeKnowledge(OwnerPrincipal principal, String sourceId) {
        requireOwner(principal);
        KnowledgeSource revoked = knowledgeService.revokeSource(sourceId);
        securityActivityService.record(
                principal,
                ResourceType.KNOWLEDGE_SOURCE,
                sourceId,
                "REVOKE_KNOWLEDGE_SOURCE",
                Map.of("sourceId", sourceId));
        return revoked;
    }

    public List<PersonalSkillMetadata> listSkills(OwnerPrincipal principal, String skillName) {
        requireOwner(principal);
        return List.of();
    }

    public OperationalMetricSummary metrics(OwnerPrincipal principal) {
        requireOwner(principal);
        List<TelemetryEvent> events = telemetry.list(principal.scopeId());
        List<RagMetric> ragMetrics = knowledgeService.listMetrics(principal.scopeId());
        long failures = events.stream()
                .filter(event -> String.valueOf(event.attributes().get("status")).contains("failed"))
                .count();
        long toolCalls = toolService.listActivity(principal.scopeId()).size();
        return new OperationalMetricSummary(
                sessionStore.listSessions(principal.scopeId(), principal.ownerId(), null).size(),
                events.stream().filter(event -> event.type() == TelemetryEventType.AGENT
                        || event.type() == TelemetryEventType.MODEL).count(),
                toolCalls,
                ragMetrics.stream().filter(RagMetric::hit).count(),
                ragMetrics.stream().filter(metric -> !metric.hit()).count(),
                failures,
                events.stream().mapToLong(TelemetryEvent::durationMillis).sum(),
                knowledgeService.listFeedback(principal.scopeId()).size());
    }

    public CostUsageReport cost(OwnerPrincipal principal, String agentId, String providerId) {
        requireOwner(principal);
        List<TelemetryEvent> tokenEvents = telemetry.list(principal.scopeId()).stream()
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
                principal.scopeId(),
                agentId == null ? "" : agentId,
                providerId == null ? "" : providerId,
                tokenEvents.size(),
                estimatedTokens,
                estimatedTokens * 0.000001d);
    }

    public ConsoleActivityResult activitySearch(OwnerPrincipal principal) {
        return activitySearch(principal, new ActivitySearchFilter(null, null, null, null, null, null));
    }

    public ConsoleActivityResult activitySearch(OwnerPrincipal principal, ActivitySearchFilter filter) {
        requireOwner(principal);
        ResourceAccessPolicy policy = ResourceAccessPolicy.ownerOnly(
                principal.scopeId(),
                principal.ownerId(),
                ResourceType.ACTIVITY,
                Permission.SEARCH_ACTIVITY);
        List<SecurityActivityRecord> securityActivity =
                securityActivityService.search(principal, principal.scopeId(), policy).stream()
                        .filter(record -> record.ownerId().equals(principal.ownerId()))
                        .filter(record -> filter.matches(
                                record.ownerId(),
                                "",
                                record.resourceId(),
                                record.action(),
                                record.occurredAt()))
                        .toList();
        return new ConsoleActivityResult(
                toolService.listActivity(principal.scopeId()).stream()
                        .filter(record -> record.ownerId().equals(principal.ownerId()))
                        .filter(record -> filter.matches(
                                record.ownerId(),
                                record.sessionId(),
                                record.toolId(),
                                record.status().name(),
                                record.occurredAt()))
                        .toList(),
                securityActivity);
    }

    private static void requireOwner(OwnerPrincipal principal) {
        if (principal == null || principal.ownerId().isBlank()) {
            throw new IllegalStateException("diagnostics access is restricted");
        }
    }
}
