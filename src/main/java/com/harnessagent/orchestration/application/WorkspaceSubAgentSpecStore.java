package com.harnessagent.orchestration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.orchestration.domain.ExpertAgentDefinition;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.workspace.application.PersonalWorkspaceService;
import com.harnessagent.workspace.domain.PersonalWorkspaceLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceSubAgentSpecStore {

    private final PersonalWorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public WorkspaceSubAgentSpecStore(
            PersonalWorkspaceService workspaceService,
            ObjectMapper objectMapper) {
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
    }

    public ExpertAgentDefinition save(RuntimeContextScope context, ExpertAgentDefinition definition) {
        PersonalWorkspaceLayout layout = workspaceService.initialize(context);
        Path specFile = specFile(layout, definition.id());
        try {
            Files.createDirectories(specFile.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(specFile.toFile(), definition);
            return definition;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save subagent spec", ex);
        }
    }

    public Optional<ExpertAgentDefinition> find(RuntimeContextScope context, String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        Path specFile = specFile(workspaceService.layout(context), agentId);
        if (!Files.exists(specFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(specFile.toFile(), ExpertAgentDefinition.class));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read subagent spec", ex);
        }
    }

    private static Path specFile(PersonalWorkspaceLayout layout, String agentId) {
        return layout.subagentsDirectory()
                .resolve(safeFileName(agentId) + ".json")
                .toAbsolutePath()
                .normalize();
    }

    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }
        String normalized = value.trim();
        if (normalized.equals(".")
                || normalized.equals("..")
                || normalized.contains("/")
                || normalized.contains("\\")) {
            throw new IllegalArgumentException("agentId must be a safe file name");
        }
        return normalized;
    }
}
