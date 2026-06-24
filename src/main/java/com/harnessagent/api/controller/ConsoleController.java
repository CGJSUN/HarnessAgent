package com.harnessagent.api.controller;

import com.harnessagent.api.ApiIdentityResolver;
import com.harnessagent.api.request.SetEnabledRequest;
import com.harnessagent.api.request.UpdateAgentConfigRequest;
import com.harnessagent.api.request.UpdatePromptRequest;
import com.harnessagent.console.view.AgentManagementView;
import com.harnessagent.console.application.ActivitySearchFilter;
import com.harnessagent.console.view.ConsoleActivityResult;
import com.harnessagent.console.application.ConsoleService;
import com.harnessagent.console.view.CostUsageReport;
import com.harnessagent.console.view.KnowledgeSourceView;
import com.harnessagent.console.view.OperationalMetricSummary;
import com.harnessagent.console.view.UserConsoleView;
import com.harnessagent.security.domain.OwnerPrincipal;
import com.harnessagent.skill.domain.PersonalSkillMetadata;
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
            @RequestParam String ownerId,
            @RequestParam String agentId,
            @RequestParam(required = false) String sessionId) {
        return consoleService.userConsole(resolve(headers, ownerId), agentId, sessionId);
    }

    @GetMapping("/agents")
    public List<AgentManagementView> listAgents(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId) {
        return consoleService.listAgents(resolve(headers, ownerId));
    }

    @PatchMapping("/agents/{agentId}/prompt")
    public AgentManagementView updateAgentPrompt(
            @RequestHeader Map<String, String> headers,
            @PathVariable String agentId,
            @RequestParam String ownerId,
            @RequestBody UpdatePromptRequest request) {
        return consoleService.updateAgentPrompt(resolve(headers, ownerId), agentId, request.systemPrompt());
    }

    @PatchMapping("/agents/{agentId}/config")
    public AgentManagementView updateAgentConfig(
            @RequestHeader Map<String, String> headers,
            @PathVariable String agentId,
            @RequestParam String ownerId,
            @RequestBody UpdateAgentConfigRequest request) {
        return consoleService.updateAgentConfig(
                resolve(headers, ownerId),
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
            @RequestParam String ownerId) {
        return consoleService.listTools(resolve(headers, ownerId));
    }

    @PatchMapping("/tools/{toolId}/enabled")
    public ToolDefinition setToolEnabled(
            @RequestHeader Map<String, String> headers,
            @PathVariable String toolId,
            @RequestParam String ownerId,
            @RequestBody SetEnabledRequest request) {
        return consoleService.setToolEnabled(resolve(headers, ownerId), toolId, request.enabled());
    }

    @GetMapping("/knowledge")
    public List<KnowledgeSourceView> listKnowledge(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId) {
        return consoleService.listKnowledge(resolve(headers, ownerId));
    }

    @PatchMapping("/knowledge/{sourceId}/revoke")
    public KnowledgeSourceView revokeKnowledge(
            @RequestHeader Map<String, String> headers,
            @PathVariable String sourceId,
            @RequestParam String ownerId) {
        return KnowledgeSourceView.from(consoleService.revokeKnowledge(resolve(headers, ownerId), sourceId));
    }

    @GetMapping("/skills")
    public List<PersonalSkillMetadata> listSkills(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId,
            @RequestParam(required = false) String skillName) {
        return consoleService.listSkills(resolve(headers, ownerId), skillName);
    }

    @GetMapping("/metrics")
    public OperationalMetricSummary metrics(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId) {
        return consoleService.metrics(resolve(headers, ownerId));
    }

    @GetMapping("/cost")
    public CostUsageReport cost(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String providerId) {
        return consoleService.cost(resolve(headers, ownerId), agentId, providerId);
    }

    @GetMapping("/activity")
    public ConsoleActivityResult recordActivity(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId,
            @RequestParam(required = false) String targetUserId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return consoleService.activitySearch(
                resolve(headers, ownerId),
                new ActivitySearchFilter(targetUserId, sessionId, resourceId, action, from, to));
    }

    private OwnerPrincipal resolve(Map<String, String> headers, String ownerId) {
        return identityResolver.resolve(headers, ownerId);
    }
}
