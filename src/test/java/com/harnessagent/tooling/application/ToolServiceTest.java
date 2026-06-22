package com.harnessagent.tooling.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.chat.domain.ContentBlock;
import com.harnessagent.chat.domain.ContentBlockType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.harnessagent.production.config.AgentWorkloadType;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.infrastructure.RuntimeTimeoutGuard;
import com.harnessagent.production.sandbox.SandboxExecutionMode;
import com.harnessagent.production.sandbox.SandboxExecutionPolicy;
import com.harnessagent.production.sandbox.SandboxExecutionPolicyService;
import com.harnessagent.production.sandbox.SandboxExecutionRequest;
import com.harnessagent.production.sandbox.SandboxExecutionResult;
import com.harnessagent.production.sandbox.SandboxExecutor;
import com.harnessagent.production.sandbox.SandboxExecutorRegistry;
import com.harnessagent.production.telemetry.RuntimeTelemetry;
import com.harnessagent.security.application.PromptInjectionGuard;
import com.harnessagent.workspace.application.PlanModeService;
import com.harnessagent.tooling.application.ToolService;
import com.harnessagent.tooling.audit.ToolAuditRecord;
import com.harnessagent.tooling.domain.ToolAuditPolicy;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolExecutionStatus;
import com.harnessagent.tooling.domain.ToolOutputSchema;
import com.harnessagent.tooling.domain.ToolParameterSchema;
import com.harnessagent.tooling.domain.ToolPendingConfirmation;
import com.harnessagent.tooling.domain.ToolPermissionPolicy;
import com.harnessagent.tooling.domain.ToolRegistration;
import com.harnessagent.tooling.domain.ToolRiskLevel;
import com.harnessagent.tooling.domain.ToolSourceType;
import com.harnessagent.tooling.execution.ToolExecutionCommand;
import com.harnessagent.tooling.execution.ToolExecutionResult;
import com.harnessagent.tooling.execution.ToolExecutor;
import com.harnessagent.tooling.persistence.InMemoryToolStore;

class ToolServiceTest {

    private final InMemoryToolStore store = new InMemoryToolStore();
    private final CountingToolExecutor executor = new CountingToolExecutor();
    private final ToolService service = new ToolService(store, List.of(executor));

    @Test
    void registersToolWithGovernanceMetadata() {
        ToolDefinition tool = service.registerTool(readOnlyRegistration());

        assertThat(tool.name()).isEqualTo("crm.customer.lookup");
        assertThat(tool.ownerSystem()).isEqualTo("CRM");
        assertThat(tool.riskLevel()).isEqualTo(ToolRiskLevel.READ_ONLY);
        assertThat(tool.parameterSchema().requiredParameters()).containsExactly("customerId");
        assertThat(tool.permissionPolicy().allowedUserIds()).containsExactly("user-a");
        assertThat(tool.auditPolicy().enabled()).isTrue();
    }

    @Test
    void registersToolWithOutputSchemaMetadata() {
        ToolDefinition tool = service.registerTool(new ToolRegistration(
                "tenant-a",
                "report.generate",
                "Generate a structured report.",
                "Reports",
                "owner-a",
                ToolSourceType.PROTOCOL,
                "protocol://reports/generate",
                ToolRiskLevel.READ_ONLY,
                false,
                true,
                schema(Set.of("reportId"), Set.of(), Map.of(), Set.of()),
                ToolOutputSchema.structured("application/json", Map.of("type", "object", "format", "tool-result")),
                permissionPolicy(),
                ToolAuditPolicy.standard()));

        assertThat(tool.sourceType()).isEqualTo(ToolSourceType.PROTOCOL);
        assertThat(tool.outputSchema().outputType()).isEqualTo("application/json");
        assertThat(tool.outputSchema().schema()).containsEntry("format", "tool-result");
    }

    @Test
    void executesReadOnlyToolAndStoresSanitizedAudit() {
        ToolDefinition tool = service.registerTool(readOnlyRegistration());

        ToolExecutionResult result = service.execute(command(
                tool,
                Map.of("customerId", "C-1", "token", "secret"),
                false,
                null));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        assertThat(executor.invocations).isEqualTo(1);
        ToolAuditRecord audit = service.listAudit("tenant-a").get(0);
        assertThat(audit.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        assertThat(audit.sanitizedInput()).containsEntry("token", "[REDACTED]");
        assertThat(audit.sanitizedOutput()).containsEntry("email", "[REDACTED]");
        @SuppressWarnings("unchecked")
        Map<String, Object> sanitizedRequest = (Map<String, Object>) audit.sanitizedOutput().get("request");
        assertThat(sanitizedRequest).containsEntry("token", "[REDACTED]");
    }

    @Test
    void planModeAllowsReadOnlyToolsAndDoesNotExposeInternalMarkerToExecutor() {
        ToolDefinition tool = service.registerTool(readOnlyRegistration());

        ToolExecutionResult result = service.execute(command(
                tool,
                Map.of("customerId", "C-1", PlanModeService.PLAN_MODE_PARAMETER, true),
                false,
                null));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) result.output().get("request");
        assertThat(request).containsOnly(Map.entry("customerId", "C-1"));
    }

    @Test
    void planModeRejectsSideEffectToolsBeforeConfirmationOrExecution() {
        ToolDefinition tool = service.registerTool(mutatingRegistration(ToolRiskLevel.HIGH_RISK));

        ToolExecutionResult result = service.execute(command(
                tool,
                Map.of(
                        "ticketId", "T-1",
                        "status", "approved",
                        PlanModeService.PLAN_MODE_PARAMETER, true),
                false,
                "plan-1"));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED);
        assertThat(result.message()).contains("Plan mode is read-only");
        assertThat(executor.invocations).isZero();
    }

    @Test
    void classifiesMutatingToolsAsHighRiskAndRequiresConfirmation() {
        ToolDefinition tool = service.registerTool(mutatingRegistration(ToolRiskLevel.READ_ONLY));

        ToolExecutionResult result = service.execute(command(
                tool,
                Map.of("ticketId", "T-1", "status", "approved"),
                false,
                "idem-1"));

        assertThat(tool.riskLevel()).isEqualTo(ToolRiskLevel.HIGH_RISK);
        assertThat(result.status()).isEqualTo(ToolExecutionStatus.PENDING_CONFIRMATION);
        assertThat(result.approvalRequired()).isTrue();
        assertThat(executor.invocations).isZero();
        assertThat(result.operationSummary()).containsKey("confirmationId");
        assertThat(service.listPendingConfirmations("tenant-a", "user-a", "agent-a", "session-a"))
                .singleElement()
                .satisfies(confirmation -> {
                    assertThat(confirmation.confirmationId()).isEqualTo(result.operationSummary().get("confirmationId"));
                    assertThat(confirmation.toolId()).isEqualTo(tool.id());
                    assertThat(confirmation.operationSummary()).containsEntry("toolName", "ticket.update");
                });
        assertThat(service.listAudit("tenant-a").get(0).status())
                .isEqualTo(ToolExecutionStatus.PENDING_CONFIRMATION);
    }

    @Test
    void rejectsWorkspacePathEscapingPersonalWorkspaceBeforeExecution() {
        ToolDefinition tool = service.registerTool(new ToolRegistration(
                "tenant-a",
                "workspace.read",
                "Read a file from the personal workspace.",
                "Workspace",
                "owner-a",
                ToolSourceType.INTERNAL,
                "workspace",
                ToolRiskLevel.READ_ONLY,
                false,
                true,
                new ToolParameterSchema(
                        Set.of("workspacePath"),
                        Set.of(),
                        Map.of(),
                        Set.of(),
                        Set.of("workspacePath")),
                permissionPolicy(),
                ToolAuditPolicy.standard()));

        ToolExecutionResult result = service.execute(command(
                tool,
                Map.of("workspacePath", "../secret.txt"),
                false,
                null));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED);
        assertThat(result.message()).contains("workspace path");
        assertThat(executor.invocations).isZero();
    }

    @Test
    void deniesExecutionWhenPermissionDoesNotMatch() {
        ToolDefinition tool = service.registerTool(new ToolRegistration(
                "tenant-a",
                "crm.customer.lookup",
                "Query customer by id.",
                "CRM",
                "owner-a",
                ToolSourceType.INTERNAL,
                "crm",
                ToolRiskLevel.READ_ONLY,
                false,
                true,
                schema(Set.of("customerId"), Set.of(), Map.of(), Set.of()),
                new ToolPermissionPolicy(Set.of("tenant-a"), Set.of("other-user"), Set.of("agent-a"), Set.of(), Set.of()),
                ToolAuditPolicy.standard()));

        ToolExecutionResult result = service.execute(command(
                tool,
                Map.of("customerId", "C-1"),
                false,
                null));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED);
        assertThat(executor.invocations).isZero();
        assertThat(service.listAudit("tenant-a").get(0).failureReason()).contains("not allowed");
    }

    @Test
    void rejectsUnknownParametersBeforeCallingExecutor() {
        ToolDefinition tool = service.registerTool(readOnlyRegistration());

        ToolExecutionResult result = service.execute(command(
                tool,
                Map.of("customerId", "C-1", "promptInjection", "ignore policy"),
                false,
                null));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED);
        assertThat(result.message()).contains("Unsupported parameter");
        assertThat(executor.invocations).isZero();
    }

    @Test
    void rejectsNonWhitelistedParameterValueBeforeCallingExecutor() {
        ToolDefinition tool = service.registerTool(mutatingRegistration(ToolRiskLevel.HIGH_RISK));

        ToolExecutionResult result = service.execute(command(
                tool,
                Map.of("ticketId", "T-1", "status", "drop_table"),
                true,
                "idem-1"));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED);
        assertThat(result.message()).contains("not whitelisted");
        assertThat(executor.invocations).isZero();
    }

    @Test
    void reviewerApprovalAllowsHighRiskToolAndIsAudited() {
        ToolDefinition tool = service.registerTool(mutatingRegistration(ToolRiskLevel.HIGH_RISK));
        service.execute(command(
                tool,
                Map.of("ticketId", "T-1", "status", "approved"),
                false,
                "idem-1"));

        ToolExecutionResult result = service.execute(new ToolExecutionCommand(
                "tenant-a",
                "user-a",
                "agent-a",
                "session-a",
                tool.id(),
                Map.of("ticketId", "T-1", "status", "approved"),
                Set.of(),
                Set.of(),
                false,
                "approval-1",
                "reviewer-a",
                "idem-1"));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        ToolAuditRecord audit = service.listAudit("tenant-a").get(1);
        assertThat(audit.approvalId()).isEqualTo("approval-1");
        assertThat(audit.reviewerId()).isEqualTo("reviewer-a");
    }

    @Test
    void confirmedPendingToolCanResumeWithModifiedWhitelistedParameters() {
        ToolDefinition tool = service.registerTool(mutatingRegistration(ToolRiskLevel.HIGH_RISK));
        ToolExecutionResult firstPending = service.execute(command(
                tool,
                Map.of("ticketId", "T-1", "status", "approved"),
                false,
                "idem-1"));

        ToolExecutionResult result = service.execute(command(
                tool,
                Map.of("ticketId", "T-1", "status", "rejected"),
                true,
                "idem-1"));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.PENDING_CONFIRMATION);
        assertThat(result.operationSummary()).containsEntry("modifiedParameters", true);
        assertThat(result.operationSummary().get("confirmationId"))
                .isNotEqualTo(firstPending.operationSummary().get("confirmationId"));
        assertThat(service.listPendingConfirmations("tenant-a", "user-a", "agent-a", "session-a")).hasSize(1);
        assertThat(executor.invocations).isZero();
    }

    @Test
    void resumeConfirmationUsesStoredIdempotencyKeyAndCannotBeReplayed() {
        ToolDefinition tool = service.registerTool(mutatingRegistration(ToolRiskLevel.HIGH_RISK));
        ToolExecutionResult pending = service.execute(command(
                tool,
                Map.of("ticketId", "T-1", "status", "approved"),
                false,
                "idem-1"));
        String confirmationId = String.valueOf(pending.operationSummary().get("confirmationId"));

        ToolExecutionResult first = service.resumeConfirmation(
                confirmationId,
                com.harnessagent.tooling.domain.ToolConfirmationAction.CONFIRM,
                new ToolExecutionCommand(
                        "tenant-a",
                        "user-a",
                        "agent-a",
                        "session-a",
                        confirmationId,
                        Map.of(),
                        Set.of(),
                        Set.of(),
                        true,
                        "approval-1",
                        "reviewer-a",
                        "evil-idem"));
        ToolExecutionResult replay = service.resumeConfirmation(
                confirmationId,
                com.harnessagent.tooling.domain.ToolConfirmationAction.CONFIRM,
                new ToolExecutionCommand(
                        "tenant-a",
                        "user-a",
                        "agent-a",
                        "session-a",
                        confirmationId,
                        Map.of(),
                        Set.of(),
                        Set.of(),
                        true,
                        "approval-2",
                        "reviewer-b",
                        "evil-idem-2"));

        assertThat(first.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        assertThat(replay.status()).isEqualTo(ToolExecutionStatus.DENIED);
        assertThat(replay.message()).contains("not active");
        assertThat(executor.invocations).isEqualTo(1);
        assertThat(service.listAudit("tenant-a").get(1).idempotencyKey()).isEqualTo("idem-1");
    }

    @Test
    void resumeConfirmationCanRejectWithoutReplayingParameters() {
        ToolDefinition tool = service.registerTool(mutatingRegistration(ToolRiskLevel.HIGH_RISK));
        ToolExecutionResult pending = service.execute(command(
                tool,
                Map.of("ticketId", "T-1", "status", "approved"),
                false,
                "idem-1"));
        String confirmationId = String.valueOf(pending.operationSummary().get("confirmationId"));

        ToolExecutionResult rejected = service.resumeConfirmation(
                confirmationId,
                com.harnessagent.tooling.domain.ToolConfirmationAction.REJECT,
                new ToolExecutionCommand(
                        "tenant-a",
                        "user-a",
                        "agent-a",
                        "session-a",
                        confirmationId,
                        Map.of(),
                        Set.of(),
                        Set.of(),
                        false,
                        "approval-1",
                        "reviewer-a",
                        "ignored-idem"));

        assertThat(rejected.status()).isEqualTo(ToolExecutionStatus.DENIED);
        assertThat(rejected.message()).contains("rejected by user");
        assertThat(service.listPendingConfirmations("tenant-a", "user-a", "agent-a", "session-a")).isEmpty();
        assertThat(executor.invocations).isZero();
    }

    @Test
    void reusesIdempotentResultForConfirmedMutatingTool() {
        ToolDefinition tool = service.registerTool(mutatingRegistration(ToolRiskLevel.HIGH_RISK));

        ToolExecutionCommand first = command(
                tool,
                Map.of("ticketId", "T-1", "status", "approved"),
                true,
                "idem-1");
        service.execute(command(
                tool,
                Map.of("ticketId", "T-1", "status", "approved"),
                false,
                "idem-1"));
        ToolExecutionResult firstResult = service.execute(first);
        ToolExecutionResult duplicate = service.execute(first);

        assertThat(firstResult.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        assertThat(duplicate.status()).isEqualTo(ToolExecutionStatus.DUPLICATE);
        assertThat(duplicate.output()).isEqualTo(firstResult.output());
        assertThat(executor.invocations).isEqualTo(1);
    }

    @Test
    void rejectsPendingHighRiskToolWithoutCallingExecutorAndAuditsDecision() {
        ToolDefinition tool = service.registerTool(mutatingRegistration(ToolRiskLevel.HIGH_RISK));

        ToolExecutionResult result = service.reject(command(
                tool,
                Map.of("ticketId", "T-1", "status", "approved"),
                false,
                "idem-1"));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED);
        assertThat(result.message()).contains("rejected by user");
        assertThat(executor.invocations).isZero();
        ToolAuditRecord audit = service.listAudit("tenant-a").get(0);
        assertThat(audit.status()).isEqualTo(ToolExecutionStatus.DENIED);
        assertThat(audit.idempotencyKey()).isEqualTo("idem-1");
    }

    @Test
    void rejectsIdempotentRetryWhenParametersChange() {
        ToolDefinition tool = service.registerTool(mutatingRegistration(ToolRiskLevel.HIGH_RISK));

        service.execute(command(
                tool,
                Map.of("ticketId", "T-1", "status", "approved"),
                false,
                "idem-1"));
        ToolExecutionResult firstResult = service.execute(command(
                tool,
                Map.of("ticketId", "T-1", "status", "approved"),
                true,
                "idem-1"));
        service.execute(command(
                tool,
                Map.of("ticketId", "T-1", "status", "rejected"),
                false,
                "idem-1"));
        ToolExecutionResult conflict = service.execute(command(
                tool,
                Map.of("ticketId", "T-1", "status", "rejected"),
                true,
                "idem-1"));

        assertThat(firstResult.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        assertThat(conflict.status()).isEqualTo(ToolExecutionStatus.IDEMPOTENCY_CONFLICT);
        assertThat(executor.invocations).isEqualTo(1);
    }

    @Test
    void appliesSameGovernanceToMcpBackedTools() {
        ToolDefinition tool = service.registerTool(new ToolRegistration(
                "tenant-a",
                "mcp.finance.lookup",
                "Lookup finance data through an approved MCP server.",
                "Finance MCP",
                "owner-a",
                ToolSourceType.MCP,
                "mcp://finance/read",
                ToolRiskLevel.READ_ONLY,
                false,
                true,
                schema(Set.of("accountId"), Set.of(), Map.of(), Set.of()),
                permissionPolicy(),
                ToolAuditPolicy.standard()));

        ToolExecutionResult result = service.execute(command(
                tool,
                Map.of("accountId", "A-1"),
                false,
                null));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        ToolAuditRecord audit = service.listAudit("tenant-a").get(0);
        assertThat(audit.sourceType()).isEqualTo(ToolSourceType.MCP);
        assertThat(audit.toolName()).isEqualTo("mcp.finance.lookup");
    }

    @Test
    void appliesSameGovernanceToProtocolBackedTools() {
        ToolDefinition tool = service.registerTool(new ToolRegistration(
                "tenant-a",
                "protocol.calendar.lookup",
                "Lookup calendar data through an approved protocol adapter.",
                "Calendar Protocol",
                "owner-a",
                ToolSourceType.PROTOCOL,
                "protocol://calendar/read",
                ToolRiskLevel.READ_ONLY,
                false,
                true,
                schema(Set.of("calendarId"), Set.of(), Map.of(), Set.of()),
                permissionPolicy(),
                ToolAuditPolicy.standard()));

        ToolExecutionResult result = service.execute(command(
                tool,
                Map.of("calendarId", "cal-1"),
                false,
                null));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        assertThat(service.listAudit("tenant-a").get(0).sourceType()).isEqualTo(ToolSourceType.PROTOCOL);
    }

    @Test
    void mapsToolExecutionResultToContentBlockWithSummaryAndRawReference() {
        ToolExecutionResult result = ToolExecutionResult.success(
                "tool-a",
                Map.of(
                        "summary", "generated",
                        "rawReference", "workspace://artifacts/tool-a/raw.json",
                        "rows", List.of(Map.of("id", "R-1"))));

        ContentBlock block = ToolResultContentMapper.toContentBlock("report.generate", result);

        assertThat(block.type()).isEqualTo(ContentBlockType.TOOL_RESULT);
        assertThat(block.metadata()).containsEntry("toolName", "report.generate");
        @SuppressWarnings("unchecked")
        Map<String, Object> structured = (Map<String, Object>) block.metadata().get("result");
        assertThat(structured)
                .containsEntry("status", ToolExecutionStatus.SUCCEEDED.name())
                .containsEntry("rawReference", "workspace://artifacts/tool-a/raw.json");
        assertThat(structured).containsKey("output");
    }

    @Test
    void sandboxedShellToolRequiresConfirmationBeforeExecution() {
        ToolDefinition tool = service.registerTool(shellRegistration());

        ToolExecutionResult result = service.execute(command(
                tool,
                Map.of("command", "pwd"),
                false,
                null));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.PENDING_CONFIRMATION);
        assertThat(result.approvalRequired()).isTrue();
        assertThat(result.operationSummary())
                .containsEntry("sandboxRequired", true)
                .containsEntry("workloadType", AgentWorkloadType.SHELL.name());
        assertThat(executor.invocations).isZero();
    }

    @Test
    void confirmedSandboxedShellToolUsesSandboxExecutor() {
        RecordingSandboxExecutor sandboxExecutor = new RecordingSandboxExecutor();
        ToolService sandboxedService = sandboxedToolService(sandboxExecutor);
        ToolDefinition tool = sandboxedService.registerTool(shellRegistration());
        sandboxedService.execute(command(
                tool,
                Map.of("command", "pwd"),
                false,
                null));

        ToolExecutionResult result = sandboxedService.execute(command(
                tool,
                Map.of("command", "pwd"),
                true,
                null));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        assertThat(executor.invocations).isZero();
        assertThat(sandboxExecutor.invocations).isEqualTo(1);
        assertThat(sandboxExecutor.lastRequest.workloadType()).isEqualTo(AgentWorkloadType.SHELL);
        assertThat(sandboxExecutor.lastRequest.command()).isEqualTo("pwd");
        assertThat(result.output()).containsEntry("stdout", "sandbox-ok");
        @SuppressWarnings("unchecked")
        Map<String, Object> sandbox = (Map<String, Object>) result.output().get("sandbox");
        assertThat(sandbox).containsEntry("mode", SandboxExecutionMode.LOCAL_PROCESS.name());
        ToolAuditRecord audit = sandboxedService.listAudit("tenant-a").get(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> stdoutSummary = (Map<String, Object>) audit.sanitizedOutput().get("stdout");
        assertThat(stdoutSummary).containsEntry("size", "sandbox-ok".length());
        assertThat(stdoutSummary).containsKey("sha256");
    }

    @Test
    void directConfirmedSandboxedToolWithoutPendingConfirmationIsDenied() {
        RecordingSandboxExecutor sandboxExecutor = new RecordingSandboxExecutor();
        ToolService sandboxedService = sandboxedToolService(sandboxExecutor);
        ToolDefinition tool = sandboxedService.registerTool(shellRegistration());

        ToolExecutionResult result = sandboxedService.execute(command(
                tool,
                Map.of("command", "pwd"),
                true,
                null));

        assertThat(result.status()).isEqualTo(ToolExecutionStatus.DENIED);
        assertThat(result.message()).contains("No matching pending confirmation");
        assertThat(sandboxExecutor.invocations).isZero();
    }

    @Test
    void dangerousExecutionParametersFailClosedToSandboxEvenWithoutExplicitWorkloadType() {
        RecordingSandboxExecutor sandboxExecutor = new RecordingSandboxExecutor();
        ToolService sandboxedService = sandboxedToolService(sandboxExecutor);
        ToolDefinition tool = sandboxedService.registerTool(new ToolRegistration(
                "tenant-a",
                "custom.runner",
                "Custom internal runner.",
                "Runner",
                "owner-a",
                ToolSourceType.INTERNAL,
                "runner",
                ToolRiskLevel.READ_ONLY,
                false,
                true,
                schema(Set.of("command"), Set.of(), Map.of(), Set.of()),
                permissionPolicy(),
                ToolAuditPolicy.standard()));

        ToolExecutionResult pending = sandboxedService.execute(command(
                tool,
                Map.of("command", "pwd"),
                false,
                null));
        ToolExecutionResult confirmed = sandboxedService.execute(command(
                tool,
                Map.of("command", "pwd"),
                true,
                null));

        assertThat(pending.status()).isEqualTo(ToolExecutionStatus.PENDING_CONFIRMATION);
        assertThat(pending.operationSummary()).containsEntry("workloadType", AgentWorkloadType.UNTRUSTED.name());
        assertThat(confirmed.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        assertThat(sandboxExecutor.invocations).isEqualTo(1);
    }

    private static ToolRegistration readOnlyRegistration() {
        return new ToolRegistration(
                "tenant-a",
                "crm.customer.lookup",
                "Query customer by id.",
                "CRM",
                "owner-a",
                ToolSourceType.INTERNAL,
                "crm",
                ToolRiskLevel.READ_ONLY,
                false,
                true,
                schema(Set.of("customerId"), Set.of("token"), Map.of(), Set.of("token")),
                permissionPolicy(),
                ToolAuditPolicy.enabled(Set.of("token"), Set.of("email")));
    }

    private static ToolRegistration mutatingRegistration(ToolRiskLevel requestedRisk) {
        return new ToolRegistration(
                "tenant-a",
                "ticket.update",
                "Update a ticket in the service desk.",
                "ServiceDesk",
                "owner-a",
                ToolSourceType.INTERNAL,
                "service-desk",
                requestedRisk,
                true,
                true,
                schema(
                        Set.of("ticketId", "status"),
                        Set.of(),
                        Map.of("status", Set.of("approved", "rejected")),
                        Set.of()),
                permissionPolicy(),
                ToolAuditPolicy.standard());
    }

    private static ToolRegistration shellRegistration() {
        return new ToolRegistration(
                "tenant-a",
                "shell.run",
                "Run an approved shell command in the personal workspace sandbox.",
                "Local Shell",
                "owner-a",
                ToolSourceType.INTERNAL,
                "shell://local",
                ToolRiskLevel.READ_ONLY,
                false,
                true,
                schema(Set.of("command"), Set.of("arguments", "environment"), Map.of(), Set.of()),
                permissionPolicy(),
                ToolAuditPolicy.standard(),
                AgentWorkloadType.SHELL);
    }

    private static ToolService sandboxedToolService(SandboxExecutor sandboxExecutor) {
        ProductionRuntimeProperties properties = new ProductionRuntimeProperties();
        InMemoryToolStore store = new InMemoryToolStore();
        return new ToolService(
                store,
                List.of(new CountingToolExecutor()),
                new RuntimeTimeoutGuard(properties),
                RuntimeTelemetry.noop(),
                new PromptInjectionGuard(),
                new SandboxExecutionPolicyService(properties),
                new SandboxExecutorRegistry(List.of(sandboxExecutor)));
    }

    private static ToolParameterSchema schema(
            Set<String> required,
            Set<String> optional,
            Map<String, Set<String>> allowedValues,
            Set<String> sensitive) {
        return new ToolParameterSchema(required, optional, allowedValues, sensitive);
    }

    private static ToolPermissionPolicy permissionPolicy() {
        return new ToolPermissionPolicy(
                Set.of("tenant-a"),
                Set.of("user-a"),
                Set.of("agent-a"),
                Set.of(),
                Set.of());
    }

    private static ToolExecutionCommand command(
            ToolDefinition tool,
            Map<String, Object> parameters,
            boolean confirmed,
            String idempotencyKey) {
        return new ToolExecutionCommand(
                "tenant-a",
                "user-a",
                "agent-a",
                "session-a",
                tool.id(),
                parameters,
                Set.of(),
                Set.of(),
                confirmed,
                null,
                null,
                idempotencyKey);
    }

    private static class CountingToolExecutor implements ToolExecutor {

        private int invocations;

        @Override
        public boolean supports(ToolDefinition definition) {
            return true;
        }

        @Override
        public Map<String, Object> execute(ToolDefinition definition, Map<String, Object> parameters) {
            invocations++;
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("tool", definition.name());
            output.put("request", parameters);
            output.put("email", "person@example.com");
            output.put("invocation", invocations);
            return output;
        }
    }

    private static class RecordingSandboxExecutor implements SandboxExecutor {

        private int invocations;
        private SandboxExecutionRequest lastRequest;

        @Override
        public SandboxExecutionMode mode() {
            return SandboxExecutionMode.LOCAL_PROCESS;
        }

        @Override
        public SandboxExecutionResult execute(SandboxExecutionPolicy policy, SandboxExecutionRequest request) {
            invocations++;
            lastRequest = request;
            return SandboxExecutionResult.succeeded(0, "sandbox-ok", "", Map.of("runner", "test"));
        }
    }
}
