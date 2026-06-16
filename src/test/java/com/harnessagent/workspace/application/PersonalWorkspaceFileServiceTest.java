package com.harnessagent.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.chat.domain.ContentBlockType;
import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.workspace.domain.PersonalWorkspaceFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersonalWorkspaceFileServiceTest {

    private final RuntimeContextFactory contextFactory = new RuntimeContextFactory();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void savesLocatesDownloadsAndDeletesWorkspaceFile() {
        PersonalWorkspaceFileService service = service();
        RuntimeContextScope context = contextFactory.create("personal", "owner-a", "personal-agent", "session-a");

        PersonalWorkspaceFile saved = service.upload(
                context,
                "artifacts/report.md",
                "hello workspace".getBytes(StandardCharsets.UTF_8),
                "text/markdown");

        assertThat(saved.uri()).isEqualTo("workspace://artifacts/report.md");
        assertThat(saved.relativePath()).isEqualTo("artifacts/report.md");
        assertThat(saved.size()).isEqualTo("hello workspace".getBytes(StandardCharsets.UTF_8).length);
        assertThat(saved.asContentBlock().type()).isEqualTo(ContentBlockType.FILE);
        assertThat(saved.asContentBlock().uri()).isEqualTo("workspace://artifacts/report.md");
        assertThat(service.locate(context, saved.uri(), "text/markdown").referenceMetadata())
                .containsEntry("fileName", "report.md")
                .containsEntry("mimeType", "text/markdown");
        assertThat(new String(service.download(context, saved.uri()), StandardCharsets.UTF_8))
                .isEqualTo("hello workspace");

        assertThat(service.delete(context, saved.uri())).isTrue();
        assertThatThrownBy(() -> service.download(context, saved.uri()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void generatedFilesUseSameWorkspaceReferences() {
        PersonalWorkspaceFileService service = service();
        RuntimeContextScope context = contextFactory.create("personal", "owner-a", "personal-agent", "session-a");

        PersonalWorkspaceFile saved = service.saveGeneratedFile(
                context,
                "sessions/session-a/summary.txt",
                "summary".getBytes(StandardCharsets.UTF_8),
                "text/plain");

        assertThat(saved.uri()).isEqualTo("workspace://sessions/session-a/summary.txt");
        assertThat(saved.fileName()).isEqualTo("summary.txt");
        assertThat(new String(service.download(context, "sessions/session-a/summary.txt"), StandardCharsets.UTF_8))
                .isEqualTo("summary");
    }

    @Test
    void rejectsPathTraversalAndOwnerCrossAccess() {
        PersonalWorkspaceFileService service = service();
        RuntimeContextScope ownerA = contextFactory.create("personal", "owner-a", "personal-agent", "session-a");
        RuntimeContextScope ownerB = contextFactory.create("personal", "owner-b", "personal-agent", "session-a");
        PersonalWorkspaceFile saved = service.upload(
                ownerA,
                "artifacts/private.txt",
                "secret".getBytes(StandardCharsets.UTF_8),
                "text/plain");

        assertThatThrownBy(() -> service.upload(
                        ownerA,
                        "../outside.txt",
                        "bad".getBytes(StandardCharsets.UTF_8),
                        "text/plain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes personal workspace");
        assertThatThrownBy(() -> service.download(ownerB, saved.uri()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    private PersonalWorkspaceFileService service() {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setWorkspace(tempDir.resolve("workspaces/personal-agent").toString());
        properties.getAgents().put("personal-agent", agent);
        return new PersonalWorkspaceFileService(new PersonalWorkspaceService(properties, objectMapper));
    }
}
