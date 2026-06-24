package com.harnessagent.api.controller;

import com.harnessagent.api.ApiIdentityResolver;
import com.harnessagent.api.request.PersonalPlanRequest;
import com.harnessagent.api.request.WorkspaceFileUploadRequest;
import com.harnessagent.api.response.PersonalPlanResponse;
import com.harnessagent.api.response.WorkspaceFilePreviewResponse;
import com.harnessagent.api.response.WorkspaceFileResponse;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.security.domain.OwnerPrincipal;
import com.harnessagent.workspace.application.PersonalWorkspaceFileService;
import com.harnessagent.workspace.application.PersonalWorkspaceService;
import com.harnessagent.workspace.application.PlanModeService;
import com.harnessagent.workspace.domain.PersonalWorkspaceFile;
import com.harnessagent.workspace.domain.PersonalWorkspaceLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private static final int PREVIEW_LIMIT_BYTES = 64 * 1024;
    private static final String DEFAULT_SESSION_ID = "workbench";

    private final ApiIdentityResolver identityResolver;
    private final RuntimeContextFactory runtimeContextFactory;
    private final PersonalWorkspaceService workspaceService;
    private final PersonalWorkspaceFileService fileService;
    private final PlanModeService planModeService;

    public WorkspaceController(
            ApiIdentityResolver identityResolver,
            RuntimeContextFactory runtimeContextFactory,
            PersonalWorkspaceService workspaceService,
            PersonalWorkspaceFileService fileService,
            PlanModeService planModeService) {
        this.identityResolver = identityResolver;
        this.runtimeContextFactory = runtimeContextFactory;
        this.workspaceService = workspaceService;
        this.fileService = fileService;
        this.planModeService = planModeService;
    }

    @GetMapping("/files")
    public List<WorkspaceFileResponse> listFiles(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId,
            @RequestParam String agentId,
            @RequestParam(required = false) String sessionId) {
        RuntimeContextScope context = context(headers, ownerId, agentId, sessionId);
        PersonalWorkspaceLayout layout = workspaceService.initialize(context);
        try (var paths = Files.walk(layout.root())) {
            return paths
                    .filter(Files::isRegularFile)
                    .sorted()
                    .map(path -> describe(context, layout, path))
                    .map(WorkspaceFileResponse::from)
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to list workspace files", ex);
        }
    }

    @GetMapping("/files/preview")
    public WorkspaceFilePreviewResponse previewFile(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId,
            @RequestParam String agentId,
            @RequestParam(required = false) String sessionId,
            @RequestParam("path") String path) {
        RuntimeContextScope context = context(headers, ownerId, agentId, sessionId);
        PersonalWorkspaceFile file = fileService.locate(context, path, contentType(context, path));
        byte[] content = fileService.download(context, path);
        boolean truncated = content.length > PREVIEW_LIMIT_BYTES;
        byte[] visible = truncated ? Arrays.copyOf(content, PREVIEW_LIMIT_BYTES) : content;
        return new WorkspaceFilePreviewResponse(
                WorkspaceFileResponse.from(file),
                new String(visible, StandardCharsets.UTF_8),
                truncated);
    }

    @PostMapping("/files")
    public WorkspaceFileResponse uploadFile(
            @RequestHeader Map<String, String> headers,
            @RequestBody WorkspaceFileUploadRequest request) {
        OwnerPrincipal principal = new OwnerPrincipal(identityResolver.resolveTrustedOwner(headers, null));
        String agentId = identityResolver.resolveTrustedAgentId(headers, request.agentId());
        RuntimeContextScope context = runtimeContextFactory.createPersonal(
                principal.ownerId(), agentId, sessionOrDefault(request.sessionId()));
        return WorkspaceFileResponse.from(fileService.upload(
                context,
                request.relativePath(),
                safeContent(request.content()),
                request.mimeType()));
    }

    @GetMapping("/files/download")
    public ResponseEntity<byte[]> downloadFile(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId,
            @RequestParam String agentId,
            @RequestParam(required = false) String sessionId,
            @RequestParam("path") String path) {
        RuntimeContextScope context = context(headers, ownerId, agentId, sessionId);
        PersonalWorkspaceFile file = fileService.locate(context, path, contentType(context, path));
        byte[] content = fileService.download(context, path);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .contentType(mediaType(file.mimeType()))
                .body(content);
    }

    @DeleteMapping("/files")
    public Map<String, Boolean> deleteFile(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId,
            @RequestParam String agentId,
            @RequestParam(required = false) String sessionId,
            @RequestParam("path") String path) {
        RuntimeContextScope context = context(headers, ownerId, agentId, sessionId);
        return Map.of("deleted", fileService.delete(context, path));
    }

    @GetMapping("/plans")
    public List<PersonalPlanResponse> listPlans(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId,
            @RequestParam String agentId,
            @RequestParam(required = false) String sessionId) {
        return planModeService.listPlans(context(headers, ownerId, agentId, sessionId)).stream()
                .map(PersonalPlanResponse::from)
                .toList();
    }

    @PostMapping("/plans")
    public PersonalPlanResponse createPlan(
            @RequestHeader Map<String, String> headers,
            @RequestParam String ownerId,
            @RequestParam String agentId,
            @RequestParam(required = false) String sessionId,
            @RequestBody PersonalPlanRequest request) {
        return PersonalPlanResponse.from(planModeService.createPlan(
                context(headers, ownerId, agentId, sessionId),
                request.goal(),
                request.steps()));
    }

    private RuntimeContextScope context(
            Map<String, String> headers,
            String ownerId,
            String agentId,
            String sessionId) {
        OwnerPrincipal principal = new OwnerPrincipal(identityResolver.resolveTrustedOwner(headers, ownerId));
        String trustedAgentId = identityResolver.resolveTrustedAgentId(headers, agentId);
        return runtimeContextFactory.createPersonal(
                principal.ownerId(), trustedAgentId, sessionOrDefault(sessionId));
    }

    private PersonalWorkspaceFile describe(
            RuntimeContextScope context,
            PersonalWorkspaceLayout layout,
            Path path) {
        String relativePath = layout.root().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
        return fileService.locate(context, relativePath, contentType(path));
    }

    private String contentType(RuntimeContextScope context, String path) {
        return contentType(workspaceService.resolveAuthorizedPath(context, path));
    }

    private static String contentType(Path path) {
        try {
            String type = Files.probeContentType(path);
            return type == null || type.isBlank() ? "application/octet-stream" : type;
        } catch (IOException ex) {
            return "application/octet-stream";
        }
    }

    private static MediaType mediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String sessionOrDefault(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? DEFAULT_SESSION_ID : sessionId.trim();
    }

    private static byte[] safeContent(String content) {
        return (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
    }
}
