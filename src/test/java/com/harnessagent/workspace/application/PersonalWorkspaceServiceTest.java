package com.harnessagent.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.runtime.RuntimeContextFactory;
import com.harnessagent.runtime.RuntimeContextScope;
import com.harnessagent.workspace.domain.PersonalWorkspaceLayout;
import com.harnessagent.workspace.domain.PersonalWorkspaceMetadata;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersonalWorkspaceServiceTest {

    private final RuntimeContextFactory contextFactory = new RuntimeContextFactory();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void initializesPersonalWorkspaceDirectoriesAndMetadata() throws Exception {
        HarnessAgentProperties properties = properties(tempDir.resolve("workspaces/personal-agent"));
        PersonalWorkspaceService service = new PersonalWorkspaceService(properties, objectMapper);
        RuntimeContextScope context = contextFactory.create("personal", "owner-a", "personal-agent", "session-a");

        PersonalWorkspaceLayout layout = service.initialize(context);

        assertThat(layout.root()).isEqualTo(tempDir.resolve("workspaces/personal-agent/owner-a").toAbsolutePath().normalize());
        assertThat(layout.directories().keySet())
                .containsExactlyInAnyOrder("persona", "memory", "skills", "subagents", "plans", "sessions", "artifacts");
        layout.directories().values().forEach(directory -> assertThat(Files.isDirectory(directory)).isTrue());
        assertThat(Files.isRegularFile(layout.metadataFile())).isTrue();
        PersonalWorkspaceMetadata stored = objectMapper.readValue(
                layout.metadataFile().toFile(),
                PersonalWorkspaceMetadata.class);
        assertThat(stored.ownerId()).isEqualTo("owner-a");
        assertThat(stored.agentId()).isEqualTo("personal-agent");
        assertThat(stored.workspaceId()).isEqualTo("owner-a/personal-agent");
        assertThat(stored.sessionNamespace()).isEqualTo("personal-agent:*");
        assertThat(stored.directories())
                .containsOnlyKeys("persona", "memory", "skills", "subagents", "plans", "sessions", "artifacts");
    }

    @Test
    void separatesOwnersUnderSamePersonalAgentWorkspaceRoot() {
        HarnessAgentProperties properties = properties(tempDir.resolve("workspaces/personal-agent"));
        PersonalWorkspaceService service = new PersonalWorkspaceService(properties, objectMapper);

        PersonalWorkspaceLayout ownerA = service.initialize(
                contextFactory.create("personal", "owner-a", "personal-agent", "session-a"));
        PersonalWorkspaceLayout ownerB = service.initialize(
                contextFactory.create("personal", "owner-b", "personal-agent", "session-a"));

        assertThat(ownerA.root()).isNotEqualTo(ownerB.root());
        assertThat(ownerA.root().getFileName().toString()).isEqualTo("owner-a");
        assertThat(ownerB.root().getFileName().toString()).isEqualTo("owner-b");
    }

    @Test
    void layoutDescribesRequiredPathsWithoutCreatingDirectories() {
        HarnessAgentProperties properties = properties(tempDir.resolve("workspaces/personal-agent"));
        PersonalWorkspaceService service = new PersonalWorkspaceService(properties, objectMapper);

        PersonalWorkspaceLayout layout = service.layout(
                contextFactory.create("personal", "owner-a", "personal-agent", "session-a"));

        assertThat(Files.exists(layout.root())).isFalse();
        assertThat(layout.directories().keySet())
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        "persona", "memory", "skills", "subagents", "plans", "sessions", "artifacts"));
    }

    @Test
    void resolvesOnlyPathsInsidePersonalWorkspace() {
        HarnessAgentProperties properties = properties(tempDir.resolve("workspaces/personal-agent"));
        PersonalWorkspaceService service = new PersonalWorkspaceService(properties, objectMapper);
        RuntimeContextScope context = contextFactory.create("personal", "owner-a", "personal-agent", "session-a");
        PersonalWorkspaceLayout layout = service.initialize(context);

        assertThat(service.resolveAuthorizedPath(context, "artifacts/result.md"))
                .isEqualTo(layout.root().resolve("artifacts/result.md").normalize());
        assertThat(service.resolveAuthorizedPath(context, "workspace://memory/facts.json"))
                .isEqualTo(layout.root().resolve("memory/facts.json").normalize());
        assertThatThrownBy(() -> service.resolveAuthorizedPath(context, "../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes personal workspace");
        assertThatThrownBy(() -> service.resolveAuthorizedPath(context, tempDir.resolve("outside.txt").toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be relative");
    }

    private static HarnessAgentProperties properties(Path workspaceRoot) {
        HarnessAgentProperties properties = new HarnessAgentProperties();
        HarnessAgentProperties.AgentDefinition agent = new HarnessAgentProperties.AgentDefinition();
        agent.setWorkspace(workspaceRoot.toString());
        properties.getAgents().put("personal-agent", agent);
        return properties;
    }
}
