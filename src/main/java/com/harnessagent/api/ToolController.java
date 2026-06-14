package com.harnessagent.api;

import com.harnessagent.tooling.ToolAuditPolicy;
import com.harnessagent.tooling.ToolAuditRecord;
import com.harnessagent.tooling.ToolDefinition;
import com.harnessagent.tooling.ToolExecutionCommand;
import com.harnessagent.tooling.ToolExecutionResult;
import com.harnessagent.tooling.ToolParameterSchema;
import com.harnessagent.tooling.ToolPermissionPolicy;
import com.harnessagent.tooling.ToolRegistration;
import com.harnessagent.tooling.ToolService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ToolService toolService;
    private final ApiIdentityResolver identityResolver;

    public ToolController(ToolService toolService, ApiIdentityResolver identityResolver) {
        this.toolService = toolService;
        this.identityResolver = identityResolver;
    }

    @PostMapping
    public ToolDefinition register(@RequestBody ToolRegistrationRequest request) {
        return toolService.registerTool(new ToolRegistration(
                request.tenantId(),
                request.name(),
                request.description(),
                request.ownerSystem(),
                request.ownerId(),
                request.sourceType(),
                request.sourceRef(),
                request.riskLevel(),
                Boolean.TRUE.equals(request.mutating()),
                request.enabled() == null || request.enabled(),
                new ToolParameterSchema(
                        safeSet(request.requiredParameters()),
                        safeSet(request.optionalParameters()),
                        request.allowedValues(),
                        safeSet(request.sensitiveParameters())),
                new ToolPermissionPolicy(
                        singleton(request.tenantId()),
                        safeSet(request.allowedUsers()),
                        safeSet(request.allowedAgents()),
                        safeSet(request.allowedDepartments()),
                        safeSet(request.allowedRoles())),
                ToolAuditPolicy.enabled(
                        safeSet(request.sensitiveParameters()),
                        safeSet(request.sensitiveResultFields()))));
    }

    @GetMapping
    public List<ToolDefinition> list(@RequestParam String tenantId) {
        return toolService.listTools(tenantId);
    }

    @PostMapping("/execute")
    public ToolExecutionResult execute(
            @RequestHeader Map<String, String> headers,
            @RequestBody ToolExecutionApiRequest request) {
        com.harnessagent.security.SecurityPrincipal principal = identityResolver.resolve(
                headers,
                request.tenantId(),
                request.userId(),
                request.roles(),
                request.departments());
        return toolService.execute(new ToolExecutionCommand(
                principal.tenantId(),
                principal.userId(),
                request.agentId(),
                request.sessionId(),
                request.toolId(),
                request.parameters(),
                principal.departments(),
                principal.roles(),
                request.confirmed(),
                request.approvalId(),
                request.reviewerId(),
                request.idempotencyKey()));
    }

    @GetMapping("/audit")
    public List<ToolAuditRecord> listAudit(@RequestParam String tenantId) {
        return toolService.listAudit(tenantId);
    }

    private static Set<String> safeSet(Set<String> input) {
        return input == null ? Set.of() : input;
    }

    private static Set<String> singleton(String value) {
        return value == null || value.isBlank() ? Set.of() : Set.of(value.trim());
    }
}
