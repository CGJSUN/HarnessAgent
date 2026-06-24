package com.harnessagent.production.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harnessagent.runtime.RuntimeContextScope;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import com.harnessagent.production.budget.BudgetCounterStore;
import com.harnessagent.production.budget.BudgetDecision;
import com.harnessagent.production.budget.BudgetLimiter;
import com.harnessagent.production.budget.BudgetScope;
import com.harnessagent.production.config.AgentWorkloadType;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.config.RuntimeProfile;
import com.harnessagent.production.health.DurablePersistenceHealth;
import com.harnessagent.production.health.ProductionRuntimeValidator;
import com.harnessagent.production.infrastructure.InMemoryBudgetCounterStore;
import com.harnessagent.production.infrastructure.InMemoryRuntimeTelemetry;
import com.harnessagent.production.infrastructure.ModelFallbackPlanner;
import com.harnessagent.production.infrastructure.RetryableModelException;
import com.harnessagent.production.infrastructure.RuntimeTimeoutGuard;
import com.harnessagent.production.snapshot.SnapshotStoreType;
import com.harnessagent.production.state.StateStorePlan;
import com.harnessagent.production.state.StateStoreType;
import com.harnessagent.production.state.OwnerStateKeyStrategy;
import com.harnessagent.production.telemetry.TelemetryEvent;
import com.harnessagent.production.telemetry.TelemetryEventType;
import com.harnessagent.production.workspace.WorkspaceMode;
import com.harnessagent.production.workspace.WorkspacePlan;
import com.harnessagent.production.workspace.WorkspacePolicyService;

class ProductionRuntimeTest {

    @Test
    void rejectsLocalStateForProductionMultiReplicaRuntime() {
        ProductionRuntimeProperties properties = properties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        properties.setReplicaCount(2);
        properties.getStateStore().setType(StateStoreType.LOCAL_JSON);

        ProductionRuntimeValidator validator = new ProductionRuntimeValidator(properties);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("durable state");
    }

    @Test
    void rejectsLocalStateForProductionSingleReplicaRuntime() {
        ProductionRuntimeProperties properties = properties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        properties.setReplicaCount(1);
        properties.getStateStore().setType(StateStoreType.LOCAL_JSON);

        ProductionRuntimeValidator validator = new ProductionRuntimeValidator(properties);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("durable state");
    }

    @Test
    void rejectsProductionWhenTelemetryIsNeitherExportedNorDurablyStored() {
        ProductionRuntimeProperties properties = properties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        properties.getStateStore().setType(StateStoreType.REDIS);
        properties.getStateStore().setRedisUri("redis://prod:6379/0");
        properties.getStateStore().setDurableImplementationWired(true);

        ProductionRuntimeValidator validator = new ProductionRuntimeValidator(properties);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("telemetry");
    }

    @Test
    void acceptsProductionWhenOpenTelemetryExportIsConfigured() {
        ProductionRuntimeProperties properties = properties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        properties.getStateStore().setType(StateStoreType.REDIS);
        properties.getStateStore().setRedisUri("redis://prod:6379/0");
        properties.getStateStore().setDurableImplementationWired(true);
        properties.getTelemetry().setOpenTelemetryExportEnabled(true);

        StateStorePlan plan = new ProductionRuntimeValidator(properties).stateStorePlan();

        assertThat(plan.productionDurable()).isTrue();
    }

    @Test
    void rejectsProductionMysqlStateWithoutSchemaOrMigrationInfo() {
        ProductionRuntimeProperties properties = properties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        properties.getStateStore().setType(StateStoreType.MYSQL);
        properties.getStateStore().setMysqlDsn("jdbc:mysql://prod/harness_agent");
        properties.getStateStore().setDurableImplementationWired(true);
        properties.getTelemetry().setOpenTelemetryExportEnabled(true);

        ProductionRuntimeValidator validator = new ProductionRuntimeValidator(properties);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schema")
                .hasMessageContaining("migration");
    }

    @Test
    void acceptsProductionMysqlStateWithSchemaMigrationAndDurableTelemetryStore() {
        ProductionRuntimeProperties properties = properties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        properties.getStateStore().setType(StateStoreType.MYSQL);
        properties.getStateStore().setMysqlDsn("jdbc:mysql://prod/harness_agent");
        properties.getStateStore().setDurableImplementationWired(true);
        properties.getSchema().setName("harness_agent");
        properties.getSchema().setMigrationTool("flyway");
        properties.getSchema().setMigrationLocation("classpath:db/migration");
        properties.getTelemetry().setDurableStoreEnabled(true);

        StateStorePlan plan = new ProductionRuntimeValidator(properties).stateStorePlan();

        assertThat(plan.productionDurable()).isTrue();
    }

    @Test
    void capabilityValidationReportsActiveStoreSchemaAndSnapshotFailures() {
        ProductionRuntimeProperties properties = productionMysqlProperties();
        ProductionRuntimeValidator validator = new ProductionRuntimeValidator(
                properties,
                () -> DurablePersistenceHealth.failed(List.of("Missing durable persistence table: ha_agent_state")));

        assertThatThrownBy(validator::validateCapabilities)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production durable persistence capabilities failed")
                .hasMessageContaining("ha_agent_state");
    }

    @Test
    void allowsDevelopmentAndTestLocalStateWhileMarkingItNonDurable() {
        ProductionRuntimeProperties development = properties();
        development.setProfile(RuntimeProfile.DEVELOPMENT);
        development.getStateStore().setType(StateStoreType.LOCAL_JSON);

        ProductionRuntimeProperties test = properties();
        test.setProfile(RuntimeProfile.TEST);
        test.getStateStore().setType(StateStoreType.LOCAL_JSON);

        StateStorePlan developmentPlan = new ProductionRuntimeValidator(development).stateStorePlan();
        StateStorePlan testPlan = new ProductionRuntimeValidator(test).stateStorePlan();

        assertThat(developmentPlan.productionDurable()).isFalse();
        assertThat(testPlan.productionDurable()).isFalse();
        assertThat(developmentPlan.distributed()).isFalse();
        assertThat(testPlan.distributed()).isFalse();
    }

    @Test
    void acceptsRedisStateAndBuildsOwnerScopedKey() {
        ProductionRuntimeProperties properties = properties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        properties.setReplicaCount(2);
        properties.getStateStore().setType(StateStoreType.REDIS);
        properties.getStateStore().setRedisUri("redis://prod:6379/0");
        properties.getStateStore().setDurableImplementationWired(true);
        properties.getTelemetry().setOpenTelemetryExportEnabled(true);

        StateStorePlan plan = new ProductionRuntimeValidator(properties).stateStorePlan();
        String key = new OwnerStateKeyStrategy().key(new RuntimeContextScope(
                "owner-scope-a",
                "user-a",
                "agent-a",
                "session-a",
                "owner-scope-a:user-a",
                "agent-a:session-a"), "memory");

        assertThat(plan.distributed()).isTrue();
        assertThat(plan.productionDurable()).isTrue();
        assertThat(plan.location()).isEqualTo("redis://prod:6379/0");
        assertThat(key).isEqualTo("owner:user-a:agent:agent-a:session:session-a:scope:memory");
    }

    @Test
    void choosesRemoteWorkspaceForOfficeAgentsAndSandboxForCodeAgents() {
        ProductionRuntimeProperties properties = properties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        properties.getSnapshotStore().setType(SnapshotStoreType.S3);
        properties.getSnapshotStore().setUri("s3://snapshots");
        properties.getSandbox().setEnabled(true);
        WorkspacePolicyService service = new WorkspacePolicyService(properties);

        WorkspacePlan office = service.plan("office-agent", AgentWorkloadType.OFFICE, null);
        WorkspacePlan code = service.plan("code-agent", AgentWorkloadType.CODE, null);

        assertThat(office.mode()).isEqualTo(WorkspaceMode.REMOTE);
        assertThat(office.location()).contains("office-agent");
        assertThat(code.mode()).isEqualTo(WorkspaceMode.SANDBOX);
        assertThat(code.snapshotStore().type()).isEqualTo(SnapshotStoreType.S3);
    }

    @Test
    void rejectsProductionCodeWorkspaceWithoutSandbox() {
        ProductionRuntimeProperties properties = properties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        WorkspacePolicyService service = new WorkspacePolicyService(properties);

        assertThatThrownBy(() -> service.plan("code-agent", AgentWorkloadType.CODE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires sandbox");
    }

    @Test
    void recordsTelemetryForRuntimeEvents() {
        InMemoryRuntimeTelemetry telemetry = new InMemoryRuntimeTelemetry(true);
        for (TelemetryEventType type : TelemetryEventType.values()) {
            telemetry.record(
                    type,
                    "personal",
                    "owner-a",
                    "agent-a",
                    type.name().toLowerCase(),
                    Duration.ofMillis(7),
                    Map.of("status", "ok", "token", "secret-token"));
        }

        assertThat(telemetry.list("personal"))
                .extracting(TelemetryEvent::type)
                .containsExactly(TelemetryEventType.values());
        assertThat(telemetry.list("personal").get(0).attributes())
                .containsEntry("token", "[REDACTED]");
    }

    @Test
    void enforcesBudgetByOwnerAgentAndProviderScope() {
        ProductionRuntimeProperties properties = properties();
        properties.getBudget().setRequestLimit(1);
        properties.getBudget().setTokenLimit(100);
        BudgetLimiter limiter = new BudgetLimiter(properties, new InMemoryBudgetCounterStore());
        BudgetScope scope = BudgetScope.forOwner("owner-a", "agent-a", "dashscope");

        BudgetDecision first = limiter.tryConsume(scope, 10);
        BudgetDecision second = limiter.tryConsume(scope, 10);

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isFalse();
        assertThat(second.reason()).contains("request_limit_exceeded");
    }

    @Test
    void sharesBudgetCountersAcrossLimiterInstancesThroughStore() {
        ProductionRuntimeProperties properties = properties();
        properties.getBudget().setRequestLimit(1);
        properties.getBudget().setTokenLimit(100);
        BudgetCounterStore store = new InMemoryBudgetCounterStore();
        BudgetScope scope = BudgetScope.forOwner("owner-a", "agent-a", "dashscope");

        BudgetDecision first = new BudgetLimiter(properties, store).tryConsume(scope, 10);
        BudgetDecision second = new BudgetLimiter(properties, store).tryConsume(scope, 10);

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isFalse();
        assertThat(second.reason()).contains("request_limit_exceeded");
    }

    @Test
    void returnsFallbackOnlyForRetryableModelFailures() {
        ProductionRuntimeProperties properties = properties();
        properties.getFallback().setProviders(Map.of("dashscope", List.of("echo")));
        ModelFallbackPlanner planner = new ModelFallbackPlanner(properties);

        assertThat(planner.fallbackProviders("dashscope", new RetryableModelException(503, "busy")))
                .containsExactly("echo");
        assertThat(planner.fallbackProviders("dashscope", new IllegalArgumentException("bad request")))
                .isEmpty();
    }

    @Test
    void appliesTimeoutGuardToLongRunningWork() {
        ProductionRuntimeProperties properties = properties();
        RuntimeTimeoutGuard guard = new RuntimeTimeoutGuard(properties);

        StepVerifier.create(guard.guard(Mono.never(), Duration.ofMillis(10), "tool"))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("timed out"))
                .verify();
    }

    private static ProductionRuntimeProperties properties() {
        return new ProductionRuntimeProperties();
    }

    private static ProductionRuntimeProperties productionMysqlProperties() {
        ProductionRuntimeProperties properties = properties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        properties.getStateStore().setType(StateStoreType.MYSQL);
        properties.getStateStore().setMysqlDsn("jdbc:mysql://prod/harness_agent");
        properties.getStateStore().setDurableImplementationWired(true);
        properties.getSchema().setName("harness_agent");
        properties.getSchema().setMigrationTool("flyway");
        properties.getSchema().setMigrationLocation("classpath:db/migration");
        properties.getTelemetry().setDurableStoreEnabled(true);
        return properties;
    }
}
