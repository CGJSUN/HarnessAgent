package com.harnessagent.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harnessagent.runtime.RuntimeContextScope;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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
                .hasMessageContaining("distributed state");
    }

    @Test
    void acceptsRedisStateAndBuildsTenantScopedKey() {
        ProductionRuntimeProperties properties = properties();
        properties.setProfile(RuntimeProfile.PRODUCTION);
        properties.setReplicaCount(2);
        properties.getStateStore().setType(StateStoreType.REDIS);
        properties.getStateStore().setRedisUri("redis://prod:6379/0");

        StateStorePlan plan = new ProductionRuntimeValidator(properties).stateStorePlan();
        String key = new TenantStateKeyStrategy().key(new RuntimeContextScope(
                "tenant-a",
                "user-a",
                "agent-a",
                "session-a",
                "tenant-a:user-a",
                "agent-a:session-a"), "memory");

        assertThat(plan.distributed()).isTrue();
        assertThat(plan.location()).isEqualTo("redis://prod:6379/0");
        assertThat(key).isEqualTo("tenant:tenant-a:user:user-a:agent:agent-a:session:session-a:scope:memory");
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
                    "tenant-a",
                    "user-a",
                    "agent-a",
                    type.name().toLowerCase(),
                    Duration.ofMillis(7),
                    Map.of("status", "ok", "token", "secret-token"));
        }

        assertThat(telemetry.list("tenant-a"))
                .extracting(TelemetryEvent::type)
                .containsExactly(TelemetryEventType.values());
        assertThat(telemetry.list("tenant-a").get(0).attributes())
                .containsEntry("token", "[REDACTED]");
    }

    @Test
    void enforcesBudgetByTenantUserAgentAndProviderScope() {
        ProductionRuntimeProperties properties = properties();
        properties.getBudget().setRequestLimit(1);
        properties.getBudget().setTokenLimit(100);
        BudgetLimiter limiter = new BudgetLimiter(properties);
        BudgetScope scope = new BudgetScope("tenant-a", "user-a", "agent-a", "dashscope");

        BudgetDecision first = limiter.tryConsume(scope, 10);
        BudgetDecision second = limiter.tryConsume(scope, 10);

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
}
