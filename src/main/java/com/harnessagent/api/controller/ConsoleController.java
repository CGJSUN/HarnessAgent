package com.harnessagent.api.controller;

import com.harnessagent.api.ApiIdentityResolver;
import com.harnessagent.api.request.SetEnabledRequest;
import com.harnessagent.api.request.UpdateAgentConfigRequest;
import com.harnessagent.api.request.UpdatePromptRequest;
import com.harnessagent.console.view.AgentManagementView;
import com.harnessagent.console.application.AuditSearchFilter;
import com.harnessagent.console.view.ConsoleAuditResult;
import com.harnessagent.console.application.ConsoleService;
import com.harnessagent.console.view.CostUsageReport;
import com.harnessagent.console.view.KnowledgeSourceView;
import com.harnessagent.console.view.OperationalMetricSummary;
import com.harnessagent.console.view.UserConsoleView;
import com.harnessagent.security.domain.SecurityPrincipal;
import com.harnessagent.security.domain.SkillVersion;
import com.harnessagent.tooling.domain.ToolDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console")
public class ConsoleController {

    private final ConsoleService consoleService;
    private final ApiIdentityResolver identityResolver;

    public ConsoleController(ConsoleService consoleService, ApiIdentityResolver identityResolver) {
        this.consoleService = consoleService;
        this.identityResolver = identityResolver;
    }

    @GetMapping("/user")
    public UserConsoleView userConsole(
            @RequestHeader Map<String, String> headers,
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestParam String agentId,
            @RequestParam(required = false) String sessionId) {
        return consoleService.userConsole(resolve(headers, tenantId, userId), agentId, sessionId);
    }

    @GetMapping("/agents")
    public List<AgentManagementView> listAgents(
            @RequestHeader Map<String, String> headers,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return consoleService.listAgents(resolve(headers, tenantId, userId));
    }

    @PatchMapping("/agents/{agentId}/prompt")
    public AgentManagementView updateAgentPrompt(
            @RequestHeader Map<String, String> headers,
            @PathVariable String agentId,
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestBody UpdatePromptRequest request) {
        return consoleService.updateAgentPrompt(resolve(headers, tenantId, userId), agentId, request.systemPrompt());
    }

    @PatchMapping("/agents/{agentId}/config")
    public AgentManagementView updateAgentConfig(
            @RequestHeader Map<String, String> headers,
            @PathVariable String agentId,
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestBody UpdateAgentConfigRequest request) {
        return consoleService.updateAgentConfig(
                resolve(headers, tenantId, userId),
                agentId,
                request.modelProvider(),
                request.modelName(),
                request.workspace(),
                request.compaction(),
                request.maxIters());
    }

    @GetMapping("/tools")
    public List<ToolDefinition> listTools(
            @RequestHeader Map<String, String> headers,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return consoleService.listTools(resolve(headers, tenantId, userId));
    }

    @PatchMapping("/tools/{toolId}/enabled")
    public ToolDefinition setToolEnabled(
            @RequestHeader Map<String, String> headers,
            @PathVariable String toolId,
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestBody SetEnabledRequest request) {
        return consoleService.setToolEnabled(resolve(headers, tenantId, userId), toolId, request.enabled());
    }

    @GetMapping("/knowledge")
    public List<KnowledgeSourceView> listKnowledge(
            @RequestHeader Map<String, String> headers,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return consoleService.listKnowledge(resolve(headers, tenantId, userId));
    }

    @PatchMapping("/knowledge/{sourceId}/revoke")
    public KnowledgeSourceView revokeKnowledge(
            @RequestHeader Map<String, String> headers,
            @PathVariable String sourceId,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return KnowledgeSourceView.from(consoleService.revokeKnowledge(resolve(headers, tenantId, userId), sourceId));
    }

    @GetMapping("/skills")
    public List<SkillVersion> listSkills(
            @RequestHeader Map<String, String> headers,
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestParam(required = false) String skillName) {
        return consoleService.listSkills(resolve(headers, tenantId, userId), skillName);
    }

    @PatchMapping("/skills/{versionId}/approve")
    public SkillVersion approveSkill(
            @RequestHeader Map<String, String> headers,
            @PathVariable String versionId,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return consoleService.approveSkill(resolve(headers, tenantId, userId), versionId);
    }

    @PatchMapping("/skills/{versionId}/publish")
    public SkillVersion publishSkill(
            @RequestHeader Map<String, String> headers,
            @PathVariable String versionId,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return consoleService.publishSkill(resolve(headers, tenantId, userId), versionId);
    }

    @PatchMapping("/skills/{versionId}/disable")
    public SkillVersion disableSkill(
            @RequestHeader Map<String, String> headers,
            @PathVariable String versionId,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return consoleService.disableSkill(resolve(headers, tenantId, userId), versionId);
    }

    @GetMapping("/metrics")
    public OperationalMetricSummary metrics(
            @RequestHeader Map<String, String> headers,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return consoleService.metrics(resolve(headers, tenantId, userId));
    }

    @GetMapping("/cost")
    public CostUsageReport cost(
            @RequestHeader Map<String, String> headers,
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String providerId) {
        return consoleService.cost(resolve(headers, tenantId, userId), agentId, providerId);
    }

    @GetMapping("/audit")
    public ConsoleAuditResult audit(
            @RequestHeader Map<String, String> headers,
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestParam(required = false) String targetUserId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return consoleService.auditSearch(
                resolve(headers, tenantId, userId),
                new AuditSearchFilter(targetUserId, sessionId, resourceId, action, from, to));
    }

    private SecurityPrincipal resolve(Map<String, String> headers, String tenantId, String userId) {
        return identityResolver.resolve(headers, tenantId, userId, Set.of(), Set.of());
    }
}
