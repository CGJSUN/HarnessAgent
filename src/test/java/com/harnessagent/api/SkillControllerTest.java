package com.harnessagent.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.harnessagent.api.controller.SkillController;
import com.harnessagent.api.request.SkillRepositoryRefreshRequest;
import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.skill.application.PersonalSkillService;
import com.harnessagent.workspace.application.PersonalWorkspaceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsRefreshPathOutsidePersonalSkillsDirectory() throws Exception {
        PersonalSkillService skillService = mock(PersonalSkillService.class);
        SkillController controller = new SkillController(
                skillService,
                new ApiIdentityResolver(),
                new RuntimeContextFactory(),
                new PersonalWorkspaceService(properties()));
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);

        assertThatThrownBy(() -> controller.refreshLocalRepository(
                        Map.of(),
                        new SkillRepositoryRefreshRequest(
                                "owner-a",
                                "agent-a",
                                outside.toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("personal skills directory");
        verifyNoInteractions(skillService);
    }

    private HarnessAgentProperties properties() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setWorkspace(tempDir.resolve("workspaces").toString());
        properties.getAgents().put("agent-a", agent);
        return properties;
    }
}
