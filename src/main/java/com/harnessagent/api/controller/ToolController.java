package com.harnessagent.api.controller;

import com.harnessagent.api.ApiIdentityResolver;
import com.harnessagent.api.request.ToolConfirmationResumeRequest;
import com.harnessagent.api.request.ToolExecutionApiRequest;
import com.harnessagent.api.request.ToolRegistrationRequest;
import com.harnessagent.runtime.PersonalRuntimeDefaults;
import com.harnessagent.tooling.domain.ToolActivityPolicy;
import com.harnessagent.tooling.activity.ToolActivityRecord;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolOutputSchema;
import com.harnessagent.tooling.execution.ToolExecutionCommand;
import com.harnessagent.tooling.execution.ToolExecutionResult;
import com.harnessagent.tooling.domain.ToolParameterSchema;
import com.harnessagent.tooling.domain.ToolPendingConfirmation;
import com.harnessagent.tooling.domain.ToolPermissionPolicy;
import com.harnessagent.tooling.domain.ToolRegistration;
import com.harnessagent.tooling.application.ToolService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.harnessagent.security.domain.OwnerPrincipal;

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
                PersonalRuntimeDefaults.PERSONAL_SCOPE_ID,
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
                        safeSet(request.sensitiveParameters()),
                        safeSet(request.workspacePathParameters())),
                ToolOutputSchema.structured(request.outputType(), safeMap(request.outputSchema())),
                new ToolPermissionPolicy(
                        safeSet(request.allowedOwnerIds()),
                        safeSet(request.allowedAgents()),
                        safeSet(request.deniedOwnerIds())),
                ToolActivityPolicy.enabled(
                        safeSet(request.sensitiveParameters()),
                        safeSet(request.sensitiveResultFields())),
                request.workloadType()));
    }

    @GetMapping
    public List<ToolDefinition> list(@RequestParam(required = false) String ownerId) {
        return toolService.listTools(PersonalRuntimeDefaults.PERSONAL_SCOPE_ID);
    }

    @PostMapping("/execute")
    public ToolExecutionResult execute(
            @RequestHeader Map<String, String> headers,
            @RequestBody ToolExecutionApiRequest request) {
        com.harnessagent.security.domain.OwnerPrincipal principal = identityResolver.resolveTrusted(
                headers,
                request.ownerId());
        String agentId = identityResolver.resolveTrustedAgentId(headers, request.agentId());
        return toolService.execute(ToolExecutionCommand.forOwner(
                principal.ownerId(),
                agentId,
                request.sessionId(),
                request.toolId(),
                request.parameters(),
                request.confirmed(),
                request.idempotencyKey()));
    }

    @PostMapping("/reject")
    public ToolExecutionResult reject(
            @RequestHeader Map<String, String> headers,
            @RequestBody ToolExecutionApiRequest request) {
        com.harnessagent.security.domain.OwnerPrincipal principal = identityResolver.resolveTrusted(
                headers,
                request.ownerId());
        String agentId = identityResolver.resolveTrustedAgentId(headers, request.agentId());
        return toolService.reject(ToolExecutionCommand.forOwner(
                principal.ownerId(),
                agentId,
                request.sessionId(),
                request.toolId(),
                request.parameters(),
                false,
                request.idempotencyKey()));
    }

    @GetMapping("/confirmations")
    public List<ToolPendingConfirmation> listPendingConfirmations(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId,
            @RequestParam String agentId,
            @RequestParam String sessionId) {
        OwnerPrincipal principal = new OwnerPrincipal(identityResolver.resolveTrustedOwner(headers, ownerId));
        String trustedAgentId = identityResolver.resolveTrustedAgentId(headers, agentId);
        return toolService.listPendingConfirmations(
                principal.scopeId(),
                principal.ownerId(),
                trustedAgentId,
                sessionId);
    }

    @PostMapping("/confirmations/{confirmationId}/resume")
    public ToolExecutionResult resumeConfirmation(
            @RequestHeader Map<String, String> headers,
            @PathVariable String confirmationId,
            @RequestBody ToolConfirmationResumeRequest request) {
        OwnerPrincipal principal = identityResolver.resolveTrusted(headers, request.ownerId());
        String agentId = identityResolver.resolveTrustedAgentId(headers, request.agentId());
        return toolService.resumeConfirmation(
                confirmationId,
                request.action(),
                new ToolExecutionCommand(
                        principal.scopeId(),
                        principal.ownerId(),
                        agentId,
                        request.sessionId(),
                        confirmationId,
                        request.parameters(),
                        true,
                        request.idempotencyKey()));
    }

    @GetMapping("/activity")
    public List<ToolActivityRecord> listActivity(@RequestParam(required = false) String ownerId) {
        return toolService.listActivity(PersonalRuntimeDefaults.PERSONAL_SCOPE_ID);
    }

    private static Set<String> safeSet(Set<String> input) {
        return input == null ? Set.of() : input;
    }

    private static Map<String, Object> safeMap(Map<String, Object> input) {
        return input == null ? Map.of() : input;
    }
}
