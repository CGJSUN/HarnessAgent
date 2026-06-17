package com.harnessagent.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.workspace.domain.PersonalPlan;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanModeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createsReadOnlyPlanFileInsideWorkspacePlansDirectory() throws Exception {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setWorkspace(tempDir.resolve("agent-a").toString());
        properties.getAgents().put("agent-a", agent);
        PersonalWorkspaceService workspaceService = new PersonalWorkspaceService(properties);
        PlanModeService service = new PlanModeService(workspaceService);
        RuntimeContextScope context =
                new RuntimeContextFactory().create("personal", "owner-a", "agent-a", "session-a");

        PersonalPlan plan = service.createPlan(
                context,
                "Finish workspace runtime",
                List.of("Inspect existing state", "Write tests", "Verify"));

        assertThat(plan.uri()).startsWith("workspace://plans/");
        Path file = workspaceService.resolveAuthorizedPath(context, plan.uri());
        assertThat(file).startsWith(workspaceService.layout(context).plansDirectory());
        assertThat(Files.readString(file))
                .contains("# Plan: Finish workspace runtime")
                .contains("- Mode: read-only plan")
                .contains("1. Inspect existing state")
                .contains("3. Verify");
    }

    @Test
    void marksAndStripsPlanModeExecutionParameters() {
        PlanModeService service = new PlanModeService(new PersonalWorkspaceService(new HarnessAgentProperties()));

        Map<String, Object> marked = service.planModeParameters(Map.of("customerId", "C-1"));

        assertThat(PlanModeService.planModeRequested(marked)).isTrue();
        assertThat(PlanModeService.stripPlanModeParameter(marked))
                .containsOnly(Map.entry("customerId", "C-1"));
    }
}
