package com.harnessagent.api.controller;

import com.harnessagent.api.ApiIdentityResolver;
import com.harnessagent.api.request.KnowledgeDocumentRequest;
import com.harnessagent.api.request.KnowledgeRetrievalRequest;
import com.harnessagent.api.request.KnowledgeSourceRequest;
import com.harnessagent.api.request.MemoryWriteRequest;
import com.harnessagent.api.request.RagFeedbackRequest;
import com.harnessagent.runtime.PersonalRuntimeDefaults;
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
import com.harnessagent.rag.domain.OwnerRetrievalPrincipal;
import com.harnessagent.security.domain.OwnerPrincipal;
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
    public List<KnowledgeSource> listSources(@RequestParam(required = false) String ownerId) {
        return knowledgeService.listSources(PersonalRuntimeDefaults.PERSONAL_SCOPE_ID);
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
        OwnerPrincipal identity = new OwnerPrincipal(
                identityResolver.resolveTrustedOwner(headers, request.ownerId()));
        String agentId = identityResolver.resolveTrustedAgentId(headers, request.agentId());
        OwnerRetrievalPrincipal principal = OwnerRetrievalPrincipal.forOwner(identity.ownerId(), agentId);
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
            @RequestParam String ownerId,
            @RequestParam String agentId) {
        OwnerPrincipal identity = resolveOwner(headers, ownerId);
        String trustedAgentId = identityResolver.resolveTrustedAgentId(headers, agentId);
        return personalMemoryService.listMemories(identity.scopeId(), identity.ownerId(), trustedAgentId);
    }

    @PostMapping("/memory")
    public PersonalMemoryRecord requestMemoryWrite(
            @RequestHeader Map<String, String> headers,
            @RequestBody MemoryWriteRequest request) {
        OwnerPrincipal identity = resolveOwner(headers, request.ownerId());
        String agentId = identityResolver.resolveTrustedAgentId(headers, request.agentId());
        return personalMemoryService.requestWrite(new MemoryWriteCommand(
                identity.scopeId(),
                identity.ownerId(),
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
            @RequestParam String ownerId,
            @RequestParam String agentId) {
        OwnerPrincipal identity = resolveOwner(headers, ownerId);
        String trustedAgentId = identityResolver.resolveTrustedAgentId(headers, agentId);
        return personalMemoryService.confirmWrite(memoryId, identity.scopeId(), identity.ownerId(), trustedAgentId);
    }

    @PostMapping("/memory/{memoryId}/reject")
    public PersonalMemoryRecord rejectMemoryWrite(
            @RequestHeader Map<String, String> headers,
            @PathVariable String memoryId,
            @RequestParam String ownerId,
            @RequestParam String agentId) {
        OwnerPrincipal identity = resolveOwner(headers, ownerId);
        String trustedAgentId = identityResolver.resolveTrustedAgentId(headers, agentId);
        return personalMemoryService.rejectWrite(memoryId, identity.scopeId(), identity.ownerId(), trustedAgentId);
    }

    @DeleteMapping("/memory/{memoryId}")
    public PersonalMemoryRecord deleteMemory(
            @RequestHeader Map<String, String> headers,
            @PathVariable String memoryId,
            @RequestParam String ownerId,
            @RequestParam String agentId) {
        OwnerPrincipal identity = resolveOwner(headers, ownerId);
        String trustedAgentId = identityResolver.resolveTrustedAgentId(headers, agentId);
        return personalMemoryService.deleteMemory(memoryId, identity.scopeId(), identity.ownerId(), trustedAgentId);
    }

    @GetMapping("/export")
    public PersonalDataExport exportPersonalData(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId,
            @RequestParam String agentId) {
        OwnerPrincipal identity = resolveOwner(headers, ownerId);
        String trustedAgentId = identityResolver.resolveTrustedAgentId(headers, agentId);
        return personalMemoryService.exportPersonalData(identity.scopeId(), identity.ownerId(), trustedAgentId);
    }

    @GetMapping("/metrics")
    public List<RagMetric> listMetrics(@RequestParam(required = false) String ownerId) {
        return knowledgeService.listMetrics(PersonalRuntimeDefaults.PERSONAL_SCOPE_ID);
    }

    @PostMapping("/feedback")
    public RagFeedback recordFeedback(@RequestBody RagFeedbackRequest request) {
        return knowledgeService.recordFeedback(
                PersonalRuntimeDefaults.PERSONAL_SCOPE_ID,
                request.ownerId(),
                request.query(),
                request.helpful(),
                request.comment());
    }

    @GetMapping("/feedback")
    public List<RagFeedback> listFeedback(@RequestParam(required = false) String ownerId) {
        return knowledgeService.listFeedback(PersonalRuntimeDefaults.PERSONAL_SCOPE_ID);
    }

    private static KnowledgeSourceRegistration toRegistration(KnowledgeSourceRequest request) {
        return new KnowledgeSourceRegistration(
                PersonalRuntimeDefaults.PERSONAL_SCOPE_ID,
                request.ownerId(),
                request.agentId(),
                request.title(),
                request.version(),
                request.visibility(),
                safeSet(request.allowedOwnerIds()),
                request.updatePolicy(),
                request.sourceType(),
                request.sourceUri());
    }

    private static KnowledgeSourceRegistration toRegistration(KnowledgeDocumentRequest request) {
        return new KnowledgeSourceRegistration(
                PersonalRuntimeDefaults.PERSONAL_SCOPE_ID,
                request.ownerId(),
                request.agentId(),
                request.title(),
                request.version(),
                request.visibility(),
                safeSet(request.allowedOwnerIds()),
                request.updatePolicy(),
                request.sourceType(),
                request.sourceUri());
    }

    private static Set<String> safeSet(Set<String> input) {
        return input == null ? Set.of() : input;
    }

    private OwnerPrincipal resolveOwner(Map<String, String> headers, String ownerId) {
        return new OwnerPrincipal(identityResolver.resolveTrustedOwner(headers, ownerId));
    }
}
