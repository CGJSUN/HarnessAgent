package com.harnessagent.orchestration.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.orchestration.domain.ContextBoundary;
import com.harnessagent.orchestration.domain.ExpertAgentDefinition;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.workspace.application.PersonalWorkspaceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceSubAgentSpecStoreTest {

    @TempDir
    private Path tempDir;

    @Test
    void persistsSubAgentSpecUnderPersonalWorkspaceSubagentsDirectory() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.AgentDefinition supervisor = new HarnessAgentProperties.AgentDefinition();
        supervisor.setWorkspace(tempDir.toString());
        properties.getAgents().put("supervisor", supervisor);
        PersonalWorkspaceService workspaceService = new PersonalWorkspaceService(properties);
        WorkspaceSubAgentSpecStore store = new WorkspaceSubAgentSpecStore(
                workspaceService,
                new ObjectMapper().findAndRegisterModules());
        RuntimeContextScope context = new RuntimeContextFactory()
                .create("tenant-a", "user-a", "supervisor", "session-a");
        ExpertAgentDefinition spec = new ExpertAgentDefinition(
                "writer",
                "tenant-a",
                "writer",
                "撰写个人文档",
                "task",
                "draft",
                Set.of("employee"),
                Set.of("workspace.write"),
                Set.of("writing-skill"),
                Set.of("knowledge-a"),
                new ContextBoundary(false, true, false, true, Set.of("question", "draftPath")),
                "user-a",
                true,
                true,
                "v1",
                null);

        store.save(context, spec);

        assertThat(store.find(context, "writer")).contains(spec);
        assertThat(Files.exists(tempDir.resolve("user-a").resolve("subagents").resolve("writer.json"))).isTrue();
    }
}
