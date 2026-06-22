package com.harnessagent.api.controller;

import com.harnessagent.api.ApiIdentityResolver;
import com.harnessagent.api.request.KnowledgeDocumentRequest;
import com.harnessagent.api.request.KnowledgeRetrievalRequest;
import com.harnessagent.api.request.KnowledgeSourceRequest;
import com.harnessagent.api.request.MemoryWriteRequest;
import com.harnessagent.api.request.RagFeedbackRequest;
import com.harnessagent.rag.application.KnowledgeDocumentInput;
import com.harnessagent.rag.application.MemoryWriteCommand;
import com.harnessagent.rag.retrieval.KnowledgeRetrievalResult;
import com.harnessagent.rag.application.KnowledgeService;
import com.harnessagent.rag.application.PersonalMemoryService;
import com.harnessagent.rag.domain.KnowledgeSource;
import com.harnessagent.rag.domain.KnowledgeSourceRegistration;
import com.harnessagent.rag.domain.PersonalDataExport;
import com.harnessagent.rag.domain.PersonalMemoryRecord;
import com.harnessagent.rag.domain.RagFeedback;
import com.harnessagent.rag.domain.RagMetric;
import com.harnessagent.rag.domain.RetrievalPrincipal;
import com.harnessagent.security.domain.SecurityPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final PersonalMemoryService personalMemoryService;
    private final ApiIdentityResolver identityResolver;

    public KnowledgeController(
            KnowledgeService knowledgeService,
            PersonalMemoryService personalMemoryService,
            ApiIdentityResolver identityResolver) {
        this.knowledgeService = knowledgeService;
        this.personalMemoryService = personalMemoryService;
        this.identityResolver = identityResolver;
    }

    @PostMapping("/sources")
    public KnowledgeSource registerSource(@RequestBody KnowledgeSourceRequest request) {
        return knowledgeService.registerSource(toRegistration(request));
    }

    @GetMapping("/sources")
    public List<KnowledgeSource> listSources(@RequestParam String tenantId) {
        return knowledgeService.listSources(tenantId);
    }

    @PostMapping("/documents")
    public KnowledgeSource ingestDocument(@RequestBody KnowledgeDocumentRequest request) {
        return knowledgeService.ingestDocument(new KnowledgeDocumentInput(
                toRegistration(request),
                request.content()));
    }

    @PostMapping("/retrieve")
    public KnowledgeRetrievalResult retrieve(
            @RequestHeader Map<String, String> headers,
            @RequestBody KnowledgeRetrievalRequest request) {
        SecurityPrincipal identity = identityResolver.resolveTrusted(headers, request.tenantId(), request.userId());
        String agentId = identityResolver.resolveTrustedAgentId(headers, request.agentId());
        RetrievalPrincipal principal = new RetrievalPrincipal(
                identity.tenantId(),
                identity.userId(),
                agentId,
                identity.departments(),
                identity.roles());
        return knowledgeService.retrieve(principal, request.query(), request.limit());
    }

    @PostMapping("/sources/{sourceId}/revoke")
    public KnowledgeSource revokeSource(@PathVariable String sourceId) {
        return knowledgeService.revokeSource(sourceId);
    }

    @DeleteMapping("/sources/{sourceId}")
    public KnowledgeSource deleteSource(@PathVariable String sourceId) {
        return knowledgeService.deleteSource(sourceId);
    }

    @GetMapping("/memory")
    public List<PersonalMemoryRecord> listMemories(
            @RequestHeader Map<String, String> headers,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String ownerId,
            @RequestParam String agentId) {
        SecurityPrincipal identity = resolveOwner(headers, tenantId, ownerId);
        String trustedAgentId = identityResolver.resolveTrustedAgentId(headers, agentId);
        return personalMemoryService.listMemories(identity.tenantId(), identity.userId(), trustedAgentId);
    }

    @PostMapping("/memory")
    public PersonalMemoryRecord requestMemoryWrite(
            @RequestHeader Map<String, String> headers,
            @RequestBody MemoryWriteRequest request) {
        SecurityPrincipal identity = resolveOwner(headers, request.tenantId(), request.ownerId());
        String agentId = identityResolver.resolveTrustedAgentId(headers, request.agentId());
        return personalMemoryService.requestWrite(new MemoryWriteCommand(
                identity.tenantId(),
                identity.userId(),
                agentId,
                request.sessionId(),
                request.layer(),
                request.title(),
                request.content(),
                request.requireConfirmation()));
    }

    @PostMapping("/memory/{memoryId}/confirm")
    public PersonalMemoryRecord confirmMemoryWrite(
            @RequestHeader Map<String, String> headers,
            @PathVariable String memoryId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String ownerId,
            @RequestParam String agentId) {
        SecurityPrincipal identity = resolveOwner(headers, tenantId, ownerId);
        String trustedAgentId = identityResolver.resolveTrustedAgentId(headers, agentId);
        return personalMemoryService.confirmWrite(memoryId, identity.tenantId(), identity.userId(), trustedAgentId);
    }

    @PostMapping("/memory/{memoryId}/reject")
    public PersonalMemoryRecord rejectMemoryWrite(
            @RequestHeader Map<String, String> headers,
            @PathVariable String memoryId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String ownerId,
            @RequestParam String agentId) {
        SecurityPrincipal identity = resolveOwner(headers, tenantId, ownerId);
        String trustedAgentId = identityResolver.resolveTrustedAgentId(headers, agentId);
        return personalMemoryService.rejectWrite(memoryId, identity.tenantId(), identity.userId(), trustedAgentId);
    }

    @DeleteMapping("/memory/{memoryId}")
    public PersonalMemoryRecord deleteMemory(
            @RequestHeader Map<String, String> headers,
            @PathVariable String memoryId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String ownerId,
            @RequestParam String agentId) {
        SecurityPrincipal identity = resolveOwner(headers, tenantId, ownerId);
        String trustedAgentId = identityResolver.resolveTrustedAgentId(headers, agentId);
        return personalMemoryService.deleteMemory(memoryId, identity.tenantId(), identity.userId(), trustedAgentId);
    }

    @GetMapping("/export")
    public PersonalDataExport exportPersonalData(
            @RequestHeader Map<String, String> headers,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String ownerId,
            @RequestParam String agentId) {
        SecurityPrincipal identity = resolveOwner(headers, tenantId, ownerId);
        String trustedAgentId = identityResolver.resolveTrustedAgentId(headers, agentId);
        return personalMemoryService.exportPersonalData(identity.tenantId(), identity.userId(), trustedAgentId);
    }

    @GetMapping("/metrics")
    public List<RagMetric> listMetrics(@RequestParam String tenantId) {
        return knowledgeService.listMetrics(tenantId);
    }

    @PostMapping("/feedback")
    public RagFeedback recordFeedback(@RequestBody RagFeedbackRequest request) {
        return knowledgeService.recordFeedback(
                request.tenantId(),
                request.userId(),
                request.query(),
                request.helpful(),
                request.comment());
    }

    @GetMapping("/feedback")
    public List<RagFeedback> listFeedback(@RequestParam String tenantId) {
        return knowledgeService.listFeedback(tenantId);
    }

    private static KnowledgeSourceRegistration toRegistration(KnowledgeSourceRequest request) {
        return new KnowledgeSourceRegistration(
                request.tenantId(),
                request.ownerId(),
                request.agentId(),
                request.title(),
                request.version(),
                request.visibility(),
                safeSet(request.allowedDepartments()),
                safeSet(request.allowedRoles()),
                safeSet(request.allowedUsers()),
                request.updatePolicy(),
                request.sourceType(),
                request.sourceUri());
    }

    private static KnowledgeSourceRegistration toRegistration(KnowledgeDocumentRequest request) {
        return new KnowledgeSourceRegistration(
                request.tenantId(),
                request.ownerId(),
                request.agentId(),
                request.title(),
                request.version(),
                request.visibility(),
                safeSet(request.allowedDepartments()),
                safeSet(request.allowedRoles()),
                safeSet(request.allowedUsers()),
                request.updatePolicy(),
                request.sourceType(),
                request.sourceUri());
    }

    private static Set<String> safeSet(Set<String> input) {
        return input == null ? Set.of() : input;
    }

    private SecurityPrincipal resolveOwner(Map<String, String> headers, String tenantId, String ownerId) {
        return identityResolver.resolveTrusted(headers, tenantId, ownerId);
    }
}
