package com.harnessagent.api;

import com.harnessagent.console.AgentManagementView;
import com.harnessagent.console.ConsoleAuditResult;
import com.harnessagent.console.ConsoleService;
import com.harnessagent.console.CostUsageReport;
import com.harnessagent.console.AuditSearchFilter;
import com.harnessagent.console.KnowledgeSourceView;
import com.harnessagent.console.OperationalMetricSummary;
import com.harnessagent.console.UserConsoleView;
import com.harnessagent.rag.KnowledgeSource;
import com.harnessagent.security.SecurityPrincipal;
import com.harnessagent.security.SkillVersion;
import com.harnessagent.tooling.ToolDefinition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
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
    public KnowledgeSource revokeKnowledge(
            @RequestHeader Map<String, String> headers,
            @PathVariable String sourceId,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return consoleService.revokeKnowledge(resolve(headers, tenantId, userId), sourceId);
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

    public record UpdatePromptRequest(String systemPrompt) {
    }

    public record UpdateAgentConfigRequest(
            String modelProvider,
            String modelName,
            String workspace,
            Boolean compaction,
            Integer maxIters) {
    }

    public record SetEnabledRequest(boolean enabled) {
    }
}
