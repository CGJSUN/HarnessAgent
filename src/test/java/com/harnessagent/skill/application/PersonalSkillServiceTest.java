package com.harnessagent.skill.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harnessagent.security.application.AuthorizationService;
import com.harnessagent.security.application.SecurityAuditService;
import com.harnessagent.security.application.SensitiveDataRedactor;
import com.harnessagent.security.domain.IdentityProviderType;
import com.harnessagent.security.domain.ResourceType;
import com.harnessagent.security.domain.SecurityPrincipal;
import com.harnessagent.security.persistence.InMemorySecurityAuditStore;
import com.harnessagent.security.persistence.SecurityAuditRecord;
import com.harnessagent.skill.domain.PersonalSkillMetadata;
import com.harnessagent.skill.domain.PersonalSkillStatus;
import com.harnessagent.skill.domain.SkillExecutionRequest;
import com.harnessagent.skill.domain.SkillRepositoryType;
import com.harnessagent.skill.domain.SkillValidationResult;
import java.io.IOException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersonalSkillServiceTest {

    @TempDir
    private Path tempDir;

    private final InMemorySecurityAuditStore auditStore = new InMemorySecurityAuditStore();
    private final PersonalSkillService service = new PersonalSkillService(
            new LocalSkillRepositoryAdapter(),
            new AuthorizationService(),
            new SecurityAuditService(new SensitiveDataRedactor(), new AuthorizationService(), auditStore));

    @Test
    void scansLocalSkillRepositoryAndKeepsAdapterPlaceholders() throws Exception {
        Path skillDir = writeSkill(
                "file-analyzer",
                "1.0.0",
                """
                        {
                          "name": "file-analyzer",
                          "description": "Analyze workspace files",
                          "version": "1.0.0",
                          "triggers": ["analyze file", "summarize document"],
                          "permissions": {
                            "files": ["workspace://docs/**"],
                            "tools": ["workspace.read"],
                            "network": false,
                            "sandbox": false,
                            "memory": true
                          },
                          "resources": ["SKILL.md", "examples/basic.md"],
                          "agentIds": ["agent-a"]
                        }
                        """);
        write(skillDir.resolve("SKILL.md"), "Read the workspace document and produce a concise summary.");
        write(skillDir.resolve("examples/basic.md"), "Example input and output.");

        var scanned = service.refreshLocalRepository(owner(), tempDir);

        assertThat(scanned).hasSize(1);
        PersonalSkillMetadata metadata = scanned.get(0);
        assertThat(metadata.name()).isEqualTo("file-analyzer");
        assertThat(metadata.description()).isEqualTo("Analyze workspace files");
        assertThat(metadata.version()).isEqualTo("1.0.0");
        assertThat(metadata.sourceType()).isEqualTo(SkillRepositoryType.LOCAL);
        assertThat(metadata.source()).contains("file-analyzer");
        assertThat(metadata.permissions().files()).containsExactly("workspace://docs/**");
        assertThat(metadata.permissions().tools()).containsExactly("workspace.read");
        assertThat(metadata.permissions().memory()).isTrue();
        assertThat(metadata.agentIds()).containsExactly("agent-a");
        assertThat(metadata.status()).isEqualTo(PersonalSkillStatus.ENABLED);
        assertThat(service.repositoryTypes())
                .containsExactlyInAnyOrder(
                        SkillRepositoryType.LOCAL,
                        SkillRepositoryType.GIT,
                        SkillRepositoryType.MYSQL,
                        SkillRepositoryType.POSTGRESQL);
    }

    @Test
    void loadsMatchingSkillInstructionsAndResourcesIntoExecutionContext() throws Exception {
        Path skillDir = writeSkill(
                "file-analyzer",
                "1.0.0",
                """
                        {
                          "name": "file-analyzer",
                          "description": "Analyze workspace files",
                          "version": "1.0.0",
                          "triggers": ["analyze file"],
                          "permissions": {
                            "files": ["workspace://docs/**"],
                            "tools": ["workspace.read"],
                            "network": false,
                            "sandbox": false,
                            "memory": true
                          },
                          "resources": ["SKILL.md", "examples/basic.md"],
                          "agentIds": ["agent-a"]
                        }
                        """);
        write(skillDir.resolve("SKILL.md"), "Use headings and cite file paths.");
        write(skillDir.resolve("examples/basic.md"), "Analyze a workspace report and return cited headings.");
        service.refreshLocalRepository(owner(), tempDir);

        var result = service.execute(new SkillExecutionRequest(
                owner(),
                "agent-a",
                "please analyze file",
                "Summarize workspace://docs/report.md",
                Set.of("workspace.read"),
                Set.of("workspace://docs/report.md"),
                false,
                false,
                true,
                Map.of("workspaceUri", "workspace://docs/report.md")));

        assertThat(result.skillName()).isEqualTo("file-analyzer");
        assertThat(result.version()).isEqualTo("1.0.0");
        assertThat(result.injectedInstructions()).contains("Use headings");
        assertThat(result.resources()).containsKey("SKILL.md");
        assertThat(result.context()).containsEntry("workspaceUri", "workspace://docs/report.md");
    }

    @Test
    void isolatesInstalledSkillsByOwnerWithinSameTenant() throws Exception {
        Path skillDir = writeSkill("writer", "1.0.0", writerManifest("1.0.0"));
        write(skillDir.resolve("SKILL.md"), "Write owner-scoped drafts.");
        write(skillDir.resolve("examples/basic.md"), "Input: topic. Output: owner draft.");
        SecurityPrincipal ownerA = principal("personal", "owner-a");
        SecurityPrincipal ownerB = principal("personal", "owner-b");
        service.refreshLocalRepository(ownerA, tempDir);
        service.refreshLocalRepository(ownerB, tempDir);

        service.disable(ownerA, "writer", "1.0.0");

        assertThat(service.list("personal", "owner-a", null))
                .extracting(PersonalSkillMetadata::ownerId)
                .containsOnly("owner-a");
        assertThat(service.list("personal", "owner-b", null))
                .extracting(PersonalSkillMetadata::ownerId)
                .containsOnly("owner-b");
        assertThatThrownBy(() -> service.execute(new SkillExecutionRequest(
                ownerA,
                "agent-a",
                "write draft",
                "write draft",
                Set.of(),
                Set.of(),
                false,
                false,
                false,
                Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No enabled skill");
        assertThat(service.execute(new SkillExecutionRequest(
                ownerB,
                "agent-a",
                "write draft",
                "write draft",
                Set.of(),
                Set.of(),
                false,
                false,
                false,
                Map.of())).skillName()).isEqualTo("writer");
    }

    @Test
    void rejectsSkillExecutionWhenRequestedPermissionIsNotGranted() throws Exception {
        Path skillDir = writeSkill(
                "network-research",
                "1.0.0",
                """
                        {
                          "name": "network-research",
                          "description": "Research without network by default",
                          "version": "1.0.0",
                          "triggers": ["research"],
                          "permissions": {
                            "files": [],
                            "tools": [],
                            "network": false,
                            "sandbox": false,
                            "memory": false
                          },
                          "resources": ["SKILL.md", "examples/basic.md"],
                          "agentIds": ["agent-a"]
                        }
                        """);
        write(skillDir.resolve("SKILL.md"), "Research using existing context.");
        write(skillDir.resolve("examples/basic.md"), "Research using only provided context.");
        service.refreshLocalRepository(owner(), tempDir);

        assertThatThrownBy(() -> service.execute(new SkillExecutionRequest(
                owner(),
                "agent-a",
                "research",
                "Research external pricing",
                Set.of(),
                Set.of(),
                true,
                false,
                false,
                Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("network permission");
    }

    @Test
    void disablesUpgradesRollsBackAndLocksSkillVersions() throws Exception {
        Path v1 = writeSkill("writer", "1.0.0", writerManifest("1.0.0"));
        write(v1.resolve("SKILL.md"), "Write short drafts.");
        write(v1.resolve("examples/basic.md"), "Input: topic. Output: short draft.");
        Path v2 = writeSkill("writer", "1.1.0", writerManifest("1.1.0"));
        write(v2.resolve("SKILL.md"), "Write structured drafts.");
        write(v2.resolve("examples/basic.md"), "Input: topic. Output: structured draft.");
        service.refreshLocalRepository(owner(), tempDir);

        PersonalSkillMetadata disabled = service.disable(owner(), "writer", "1.0.0");
        PersonalSkillMetadata upgraded = service.upgrade(owner(), "writer", "1.1.0");
        PersonalSkillMetadata rolledBack = service.rollback(owner(), "writer", "1.0.0");
        PersonalSkillMetadata locked = service.lock(owner(), "writer", "1.0.0");

        assertThat(disabled.status()).isEqualTo(PersonalSkillStatus.DISABLED);
        assertThat(upgraded.status()).isEqualTo(PersonalSkillStatus.ENABLED);
        assertThat(rolledBack.version()).isEqualTo("1.0.0");
        assertThat(rolledBack.status()).isEqualTo(PersonalSkillStatus.ENABLED);
        assertThat(locked.status()).isEqualTo(PersonalSkillStatus.LOCKED);
        assertThatThrownBy(() -> service.upgrade(owner(), "writer", "1.1.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked");
        List<SecurityAuditRecord> auditRecords = auditStore.search(owner().tenantId(), Instant.EPOCH);
        assertThat(auditRecords)
                .extracting(SecurityAuditRecord::action)
                .contains("SKILL_DISABLED", "SKILL_UPGRADED", "SKILL_ROLLED_BACK", "SKILL_VERSION_LOCKED");
        assertThat(auditRecords)
                .anySatisfy(record -> {
                    assertThat(record.resourceType()).isEqualTo(ResourceType.SKILL);
                    assertThat(record.sanitizedDetails())
                            .containsEntry("skillName", "writer")
                            .containsKey("version");
                });
    }

    @Test
    void validatesMetadataResourcesAndExampleBeforeEnable() throws Exception {
        Path invalidDir = tempDir.resolve("broken").resolve("1.0.0");
        Files.createDirectories(invalidDir);
        write(invalidDir.resolve("skill.json"), """
                {
                  "description": "Missing required name",
                  "version": "1.0.0",
                  "triggers": [],
                  "permissions": {"network": false},
                  "resources": ["missing.md", "../outside.md"],
                  "agentIds": ["agent-a"]
                }
                """);

        SkillValidationResult result = service.validateLocalSkill(owner(), invalidDir);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .anyMatch(error -> error.contains("name is required"))
                .anyMatch(error -> error.contains("trigger is required"))
                .anyMatch(error -> error.contains("missing resource"))
                .anyMatch(error -> error.contains("resource escapes skill directory"))
                .anyMatch(error -> error.contains("execution example is required"));
        assertThatThrownBy(() -> service.enable(owner(), "broken", "1.0.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid skill version");
    }

    @Test
    void rejectsSkillResourceSymlinkEscapingSkillDirectory() throws Exception {
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        write(outside.resolve("secret.md"), "secret");
        Path skillDir = writeSkill(
                "linked-reader",
                "1.0.0",
                """
                        {
                          "name": "linked-reader",
                          "description": "Attempts to read through a link",
                          "version": "1.0.0",
                          "triggers": ["read linked"],
                          "permissions": {"network": false},
                          "resources": ["SKILL.md", "linked/secret.md"],
                          "examples": ["examples/basic.md"],
                          "agentIds": ["agent-a"]
                        }
                        """);
        write(skillDir.resolve("SKILL.md"), "Read linked content.");
        write(skillDir.resolve("examples/basic.md"), "Example.");
        try {
            Files.createSymbolicLink(skillDir.resolve("linked"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            Assumptions.assumeTrue(false, "symbolic links are not available in this test environment");
        }

        SkillValidationResult result = service.validateLocalSkill(owner(), skillDir);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                .anyMatch(error -> error.contains("resource escapes skill directory")
                        || error.contains("missing resource"));
    }

    @Test
    void refreshPreservesLockedStatusAndLockedVersionControlsActivation() throws Exception {
        Path v1 = writeSkill("locked-writer", "1.0.0", writerManifest("1.0.0", "locked-writer"));
        write(v1.resolve("SKILL.md"), "Write locked drafts.");
        write(v1.resolve("examples/basic.md"), "Input: topic. Output: locked draft.");
        service.refreshLocalRepository(owner(), tempDir);
        service.lock(owner(), "locked-writer", "1.0.0");
        Path v2 = writeSkill("locked-writer", "1.1.0", writerManifest("1.1.0", "locked-writer"));
        write(v2.resolve("SKILL.md"), "Write new drafts.");
        write(v2.resolve("examples/basic.md"), "Input: topic. Output: new draft.");
        List<PersonalSkillMetadata> refreshed = service.refreshLocalRepository(owner(), tempDir);

        assertThat(refreshed)
                .filteredOn(skill -> skill.name().equals("locked-writer") && skill.version().equals("1.0.0"))
                .singleElement()
                .extracting(PersonalSkillMetadata::status)
                .isEqualTo(PersonalSkillStatus.LOCKED);
        assertThat(refreshed)
                .filteredOn(skill -> skill.name().equals("locked-writer") && skill.version().equals("1.1.0"))
                .singleElement()
                .extracting(PersonalSkillMetadata::status)
                .isEqualTo(PersonalSkillStatus.DISABLED);
        assertThatThrownBy(() -> service.enable(owner(), "locked-writer", "1.1.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked");
    }

    private Path writeSkill(String name, String version, String manifest) throws Exception {
        Path skillDir = tempDir.resolve(name).resolve(version);
        Files.createDirectories(skillDir);
        write(skillDir.resolve("skill.json"), manifest);
        return skillDir;
    }

    private static String writerManifest(String version) {
        return writerManifest(version, "writer");
    }

    private static String writerManifest(String version, String name) {
        return """
                {
                  "name": "%s",
                  "description": "Draft writing",
                  "version": "%s",
                  "triggers": ["write draft"],
                  "permissions": {
                    "files": ["workspace://drafts/**"],
                    "tools": [],
                    "network": false,
                    "sandbox": false,
                    "memory": false
                  },
                  "resources": ["SKILL.md", "examples/basic.md"],
                  "agentIds": ["agent-a"]
                }
                """.formatted(name, version);
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static SecurityPrincipal owner() {
        return principal("personal:owner-a", "owner-a");
    }

    private static SecurityPrincipal principal(String tenantId, String ownerId) {
        return new SecurityPrincipal(
                tenantId,
                ownerId,
                IdentityProviderType.INTERNAL,
                Set.of("owner"),
                Set.of());
    }
}
