package com.harnessagent.api.controller;

import com.harnessagent.api.ApiIdentityResolver;
import com.harnessagent.api.request.SkillRepositoryRefreshRequest;
import com.harnessagent.api.request.SkillValidationRequest;
import com.harnessagent.api.response.PersonalSkillResponse;
import com.harnessagent.api.response.SkillValidationResponse;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.security.domain.OwnerPrincipal;
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
            @RequestParam String ownerId,
            @RequestParam(required = false) String skillName) {
        OwnerPrincipal principal = resolve(headers, ownerId);
        return skillService.list(principal.scopeId(), principal.ownerId(), skillName).stream()
                .map(PersonalSkillResponse::from)
                .toList();
    }

    @PostMapping("/refresh-local")
    public List<PersonalSkillResponse> refreshLocalRepository(
            @RequestHeader Map<String, String> headers,
            @RequestBody SkillRepositoryRefreshRequest request) {
        OwnerPrincipal principal = identityResolver.resolve(
                headers,
                request.ownerId());
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
        OwnerPrincipal principal = identityResolver.resolve(
                headers,
                request.ownerId());
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
            @RequestParam String ownerId) {
        return PersonalSkillResponse.from(skillService.enable(resolve(headers, ownerId), skillName, version));
    }

    @PatchMapping("/{skillName}/{version}/disable")
    public PersonalSkillResponse disable(
            @RequestHeader Map<String, String> headers,
            @PathVariable String skillName,
            @PathVariable String version,
            @RequestParam String ownerId) {
        return PersonalSkillResponse.from(skillService.disable(resolve(headers, ownerId), skillName, version));
    }

    @PatchMapping("/{skillName}/{version}/upgrade")
    public PersonalSkillResponse upgrade(
            @RequestHeader Map<String, String> headers,
            @PathVariable String skillName,
            @PathVariable String version,
            @RequestParam String ownerId) {
        return PersonalSkillResponse.from(skillService.upgrade(resolve(headers, ownerId), skillName, version));
    }

    @PatchMapping("/{skillName}/{version}/rollback")
    public PersonalSkillResponse rollback(
            @RequestHeader Map<String, String> headers,
            @PathVariable String skillName,
            @PathVariable String version,
            @RequestParam String ownerId) {
        return PersonalSkillResponse.from(skillService.rollback(resolve(headers, ownerId), skillName, version));
    }

    @PatchMapping("/{skillName}/{version}/lock")
    public PersonalSkillResponse lock(
            @RequestHeader Map<String, String> headers,
            @PathVariable String skillName,
            @PathVariable String version,
            @RequestParam String ownerId) {
        return PersonalSkillResponse.from(skillService.lock(resolve(headers, ownerId), skillName, version));
    }

    private OwnerPrincipal resolve(Map<String, String> headers, String ownerId) {
        return identityResolver.resolve(headers, ownerId);
    }

    private Path authorizedSkillPath(
            OwnerPrincipal principal,
            String agentId,
            String requestedPath,
            boolean defaultToSkillsRoot) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }
        RuntimeContextScope context = runtimeContextFactory.createPersonal(
                principal.ownerId(), agentId, "skill-repository");
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
