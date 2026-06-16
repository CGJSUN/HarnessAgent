package com.harnessagent.tooling.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.harnessagent.tooling.application.ToolService;
import com.harnessagent.tooling.audit.ToolAuditRecord;
import com.harnessagent.tooling.domain.ToolAuditPolicy;
import com.harnessagent.tooling.domain.ToolDefinition;
import com.harnessagent.tooling.domain.ToolExecutionStatus;
import com.harnessagent.tooling.domain.ToolParameterSchema;
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
        assertThat(service.listAudit("tenant-a").get(0).status())
                .isEqualTo(ToolExecutionStatus.PENDING_CONFIRMATION);
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
        ToolAuditRecord audit = service.listAudit("tenant-a").get(0);
        assertThat(audit.approvalId()).isEqualTo("approval-1");
        assertThat(audit.reviewerId()).isEqualTo("reviewer-a");
    }

    @Test
    void reusesIdempotentResultForConfirmedMutatingTool() {
        ToolDefinition tool = service.registerTool(mutatingRegistration(ToolRiskLevel.HIGH_RISK));

        ToolExecutionCommand first = command(
                tool,
                Map.of("ticketId", "T-1", "status", "approved"),
                true,
                "idem-1");
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

        ToolExecutionResult firstResult = service.execute(command(
                tool,
                Map.of("ticketId", "T-1", "status", "approved"),
                true,
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
}
