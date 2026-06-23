package com.harnessagent.api.controller;

import com.harnessagent.api.ApiIdentityResolver;
import com.harnessagent.api.request.SkillRepositoryRefreshRequest;
import com.harnessagent.api.request.SkillValidationRequest;
import com.harnessagent.api.response.PersonalSkillResponse;
import com.harnessagent.api.response.SkillValidationResponse;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.security.domain.SecurityPrincipal;
import com.harnessagent.skill.application.PersonalSkillService;
import com.harnessagent.workspace.application.PersonalWorkspaceService;
import com.harnessagent.workspace.domain.PersonalWorkspaceLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final PersonalSkillService skillService;
    private final ApiIdentityResolver identityResolver;
    private final RuntimeContextFactory runtimeContextFactory;
    private final PersonalWorkspaceService workspaceService;

    public SkillController(
            PersonalSkillService skillService,
            ApiIdentityResolver identityResolver,
            RuntimeContextFactory runtimeContextFactory,
            PersonalWorkspaceService workspaceService) {
        this.skillService = skillService;
        this.identityResolver = identityResolver;
        this.runtimeContextFactory = runtimeContextFactory;
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public List<PersonalSkillResponse> listSkills(
            @RequestHeader Map<String, String> headers,
            @RequestParam String tenantId,
            @RequestParam String userId,
            @RequestParam(required = false) String skillName) {
        SecurityPrincipal principal = resolve(headers, tenantId, userId);
        return skillService.list(principal.tenantId(), principal.userId(), skillName).stream()
                .map(PersonalSkillResponse::from)
                .toList();
    }

    @PostMapping("/refresh-local")
    public List<PersonalSkillResponse> refreshLocalRepository(
            @RequestHeader Map<String, String> headers,
            @RequestBody SkillRepositoryRefreshRequest request) {
        SecurityPrincipal principal = identityResolver.resolve(
                headers,
                request.tenantId(),
                request.userId(),
                safeSet(request.roles()),
                safeSet(request.departments()));
        return skillService.refreshLocalRepository(
                        principal,
                        authorizedSkillPath(principal, request.agentId(), request.repositoryRoot(), true))
                .stream()
                .map(PersonalSkillResponse::from)
                .toList();
    }

    @PostMapping("/validate-local")
    public SkillValidationResponse validateLocalSkill(
            @RequestHeader Map<String, String> headers,
            @RequestBody SkillValidationRequest request) {
        SecurityPrincipal principal = identityResolver.resolve(
                headers,
                request.tenantId(),
                request.userId(),
                safeSet(request.roles()),
                safeSet(request.departments()));
        return SkillValidationResponse.from(
                skillService.validateLocalSkill(
                        principal,
                        authorizedSkillPath(principal, request.agentId(), request.skillDirectory(), false)));
    }

    @PatchMapping("/{skillName}/{version}/enable")
    public PersonalSkillResponse enable(
            @RequestHeader Map<String, String> headers,
            @PathVariable String skillName,
            @PathVariable String version,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return PersonalSkillResponse.from(skillService.enable(resolve(headers, tenantId, userId), skillName, version));
    }

    @PatchMapping("/{skillName}/{version}/disable")
    public PersonalSkillResponse disable(
            @RequestHeader Map<String, String> headers,
            @PathVariable String skillName,
            @PathVariable String version,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return PersonalSkillResponse.from(skillService.disable(resolve(headers, tenantId, userId), skillName, version));
    }

    @PatchMapping("/{skillName}/{version}/upgrade")
    public PersonalSkillResponse upgrade(
            @RequestHeader Map<String, String> headers,
            @PathVariable String skillName,
            @PathVariable String version,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return PersonalSkillResponse.from(skillService.upgrade(resolve(headers, tenantId, userId), skillName, version));
    }

    @PatchMapping("/{skillName}/{version}/rollback")
    public PersonalSkillResponse rollback(
            @RequestHeader Map<String, String> headers,
            @PathVariable String skillName,
            @PathVariable String version,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return PersonalSkillResponse.from(skillService.rollback(resolve(headers, tenantId, userId), skillName, version));
    }

    @PatchMapping("/{skillName}/{version}/lock")
    public PersonalSkillResponse lock(
            @RequestHeader Map<String, String> headers,
            @PathVariable String skillName,
            @PathVariable String version,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return PersonalSkillResponse.from(skillService.lock(resolve(headers, tenantId, userId), skillName, version));
    }

    private SecurityPrincipal resolve(Map<String, String> headers, String tenantId, String userId) {
        return identityResolver.resolve(headers, tenantId, userId, Set.of(), Set.of());
    }

    private Path authorizedSkillPath(
            SecurityPrincipal principal,
            String agentId,
            String requestedPath,
            boolean defaultToSkillsRoot) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }
        RuntimeContextScope context = runtimeContextFactory.create(
                principal.tenantId(),
                principal.userId(),
                agentId,
                "skill-repository");
        PersonalWorkspaceLayout layout = workspaceService.initialize(context);
        Path skillsRoot = layout.skillsDirectory().toAbsolutePath().normalize();
        Path requested;
        if (requestedPath == null || requestedPath.isBlank()) {
            if (!defaultToSkillsRoot) {
                throw new IllegalArgumentException("skillDirectory is required");
            }
            requested = skillsRoot;
        } else {
            Path raw = Path.of(requestedPath.trim());
            requested = raw.isAbsolute() ? raw.normalize() : skillsRoot.resolve(raw).normalize();
        }
        if (!requested.startsWith(skillsRoot)) {
            throw new IllegalArgumentException("skill path must stay under the personal skills directory");
        }
        try {
            Path realSkillsRoot = skillsRoot.toRealPath();
            Path realRequested = requested.toRealPath();
            if (!realRequested.startsWith(realSkillsRoot)) {
                throw new IllegalArgumentException("skill path must stay under the personal skills directory");
            }
            return realRequested;
        } catch (IOException ex) {
            throw new IllegalArgumentException("skill path must exist under the personal skills directory", ex);
        }
    }

    private static Set<String> safeSet(Set<String> input) {
        return input == null ? Set.of() : input;
    }
}
