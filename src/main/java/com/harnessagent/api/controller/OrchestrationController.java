package com.harnessagent.api.controller;

import com.harnessagent.api.ApiIdentityResolver;
import com.harnessagent.api.request.OrchestrationApiRequest;
import com.harnessagent.orchestration.domain.AgentToolDefinition;
import com.harnessagent.orchestration.domain.ExpertAgentDefinition;
import com.harnessagent.orchestration.domain.OrchestrationRequest;
import com.harnessagent.orchestration.domain.OrchestrationResult;
import com.harnessagent.orchestration.application.OrchestrationService;
import com.harnessagent.orchestration.domain.OrchestrationTrace;
import com.harnessagent.security.domain.OwnerPrincipal;
import java.util.List;
import java.util.Map;
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
        OwnerPrincipal principal = identityResolver.resolve(
                headers,
                definition.ownerId());
        return orchestrationService.register(definition);
    }

    @PostMapping("/route")
    public OrchestrationResult route(
            @RequestHeader Map<String, String> headers,
            @RequestBody OrchestrationApiRequest request) {
        OwnerPrincipal principal = identityResolver.resolve(
                headers,
                request.ownerId());
        return orchestrationService.orchestrate(new OrchestrationRequest(
                principal,
                request.supervisorAgentId(),
                request.taskIntent(),
                request.task(),
                request.context(),
                request.delegationMode(),
                request.failureStrategy()));
    }

    @GetMapping("/agents/{agentId}/tool")
    public AgentToolDefinition asTool(@PathVariable String agentId) {
        return orchestrationService.asTool(agentId);
    }

    @GetMapping("/traces")
    public List<OrchestrationTrace> traces(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId) {
        return orchestrationService.listTraces(identityResolver.resolve(
                headers,
                ownerId));
    }
}
