package com.harnessagent.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.chat.domain.ContentBlock;
import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.session.domain.ChatMessage;
import com.harnessagent.session.domain.MessageRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextCompactionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void compactsLongContextIntoStructuredSummaryWithTraceableReferences() throws Exception {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setWorkspace(tempDir.resolve("agent-a").toString());
        agent.setCompactionMessageThreshold(3);
        properties.getAgents().put("agent-a", agent);
        PersonalWorkspaceService workspaceService = new PersonalWorkspaceService(properties);
        ContextCompactionService service = new ContextCompactionService(workspaceService, properties);
        RuntimeContextScope context =
                new RuntimeContextFactory().create("personal", "owner-a", "agent-a", "session-a");
        List<ChatMessage> messages = List.of(
                ChatMessage.user("Goal: complete the workspace runtime."),
                ChatMessage.assistant(List.of(
                        ContentBlock.text("finding: snapshots restore correctly.\nDecision: keep state in runtime.json."),
                        ContentBlock.file("workspace://artifacts/report.md", "text/markdown", "report.md"))),
                ChatMessage.user("Next: create plan mode tests."),
                ChatMessage.user("continue now"));

        List<ChatMessage> compacted = service.compactIfNeeded(context, messages);

        assertThat(compacted).hasSize(2);
        ChatMessage summary = compacted.get(0);
        assertThat(summary.role()).isEqualTo(MessageRole.SYSTEM);
        assertThat(summary.content()).contains("Goal: complete the workspace runtime.");
        assertThat(summary.content()).contains("snapshots restore correctly");
        assertThat(summary.content()).contains("workspace://artifacts/report.md");
        assertThat(compacted.get(1).content()).isEqualTo("continue now");
        String summaryUri = summary.contentBlocks().stream()
                .filter(block -> block.uri() != null)
                .findFirst()
                .orElseThrow()
                .uri();
        Path summaryFile = workspaceService.resolveAuthorizedPath(context, summaryUri);
        assertThat(Files.readString(summaryFile))
                .contains("\"goal\"")
                .contains("complete the workspace runtime")
                .contains("workspace://artifacts/report.md");
    }
}
