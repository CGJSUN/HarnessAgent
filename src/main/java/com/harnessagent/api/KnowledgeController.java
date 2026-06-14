package com.harnessagent.api;

import com.harnessagent.rag.KnowledgeDocumentInput;
import com.harnessagent.rag.KnowledgeRetrievalResult;
import com.harnessagent.rag.KnowledgeService;
import com.harnessagent.rag.KnowledgeSource;
import com.harnessagent.rag.KnowledgeSourceRegistration;
import com.harnessagent.rag.RagFeedback;
import com.harnessagent.rag.RagMetric;
import com.harnessagent.rag.RetrievalPrincipal;
import com.harnessagent.security.SecurityPrincipal;
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
    private final ApiIdentityResolver identityResolver;

    public KnowledgeController(KnowledgeService knowledgeService, ApiIdentityResolver identityResolver) {
        this.knowledgeService = knowledgeService;
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
        SecurityPrincipal identity = identityResolver.resolve(
                headers,
                request.tenantId(),
                request.userId(),
                request.roles(),
                request.departments());
        RetrievalPrincipal principal = new RetrievalPrincipal(
                identity.tenantId(),
                identity.userId(),
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
                request.title(),
                request.version(),
                request.visibility(),
                safeSet(request.allowedDepartments()),
                safeSet(request.allowedRoles()),
                safeSet(request.allowedUsers()),
                request.updatePolicy());
    }

    private static KnowledgeSourceRegistration toRegistration(KnowledgeDocumentRequest request) {
        return new KnowledgeSourceRegistration(
                request.tenantId(),
                request.ownerId(),
                request.title(),
                request.version(),
                request.visibility(),
                safeSet(request.allowedDepartments()),
                safeSet(request.allowedRoles()),
                safeSet(request.allowedUsers()),
                request.updatePolicy());
    }

    private static Set<String> safeSet(Set<String> input) {
        return input == null ? Set.of() : input;
    }
}
