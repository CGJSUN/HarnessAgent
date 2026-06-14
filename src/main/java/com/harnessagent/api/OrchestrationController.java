package com.harnessagent.api;

import com.harnessagent.orchestration.AgentToolDefinition;
import com.harnessagent.orchestration.ExpertAgentDefinition;
import com.harnessagent.orchestration.OrchestrationRequest;
import com.harnessagent.orchestration.OrchestrationResult;
import com.harnessagent.orchestration.OrchestrationService;
import com.harnessagent.orchestration.OrchestrationTrace;
import com.harnessagent.security.SecurityPrincipal;
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

@RestController
@RequestMapping("/api/orchestration")
public class OrchestrationController {

    private final OrchestrationService orchestrationService;
    private final ApiIdentityResolver identityResolver;

    public OrchestrationController(
            OrchestrationService orchestrationService,
            ApiIdentityResolver identityResolver) {
        this.orchestrationService = orchestrationService;
        this.identityResolver = identityResolver;
    }

    @PostMapping("/agents")
    public ExpertAgentDefinition register(
            @RequestHeader Map<String, String> headers,
            @RequestBody ExpertAgentDefinition definition) {
        SecurityPrincipal principal = identityResolver.resolve(
                headers,
                definition.tenantId(),
                definition.ownerId(),
                Set.of(),
                Set.of());
        if (!principal.roles().contains("admin")) {
            throw new IllegalStateException("admin role is required");
        }
        return orchestrationService.register(definition);
    }

    @PostMapping("/route")
    public OrchestrationResult route(
            @RequestHeader Map<String, String> headers,
            @RequestBody OrchestrationApiRequest request) {
        SecurityPrincipal principal = identityResolver.resolve(
                headers,
                request.tenantId(),
                request.userId(),
                request.roles(),
                request.departments());
        return orchestrationService.orchestrate(new OrchestrationRequest(
                principal,
                request.supervisorAgentId(),
                request.taskIntent(),
                request.task(),
                request.context()));
    }

    @GetMapping("/agents/{agentId}/tool")
    public AgentToolDefinition asTool(@PathVariable String agentId) {
        return orchestrationService.asTool(agentId);
    }

    @GetMapping("/traces")
    public List<OrchestrationTrace> traces(
            @RequestHeader Map<String, String> headers,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return orchestrationService.listTraces(identityResolver.resolve(
                headers,
                tenantId,
                userId,
                Set.of(),
                Set.of()));
    }

    public record OrchestrationApiRequest(
            String tenantId,
            String userId,
            Set<String> roles,
            Set<String> departments,
            String supervisorAgentId,
            String taskIntent,
            String task,
            Map<String, Object> context) {
    }
}
