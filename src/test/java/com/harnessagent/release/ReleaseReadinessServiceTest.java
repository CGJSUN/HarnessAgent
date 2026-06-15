package com.harnessagent.release;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.production.DurablePersistenceHealth;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReleaseReadinessServiceTest {

    private final ReleaseReadinessService service = new ReleaseReadinessService();

    @Test
    void exposesMvpScenarioAndPhaseGates() {
        assertThat(service.mvpScenario()).isEqualTo("企业制度知识助手");
        assertThat(service.mvpAcceptanceCriteria()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(service.phaseGates())
                .extracting(PhaseGate::status)
                .containsOnly(PhaseGateStatus.PASSED);
    }

    @Test
    void exposesRollbackActionsAndEndToEndAcceptanceReport() {
        assertThat(service.rollbackActions())
                .extracting(RollbackAction::capability)
                .contains("Agent", "Tool", "RAG", "ModelProvider", "Skill", "Supervisor");
        assertThat(service.acceptanceReport().passed()).isTrue();
    }

    @Test
    void blocksProductionRuntimeGateWhenDurablePersistenceHealthFails() {
        ReleaseReadinessService blocked = new ReleaseReadinessService(
                () -> DurablePersistenceHealth.failed(List.of("Missing durable persistence table: ha_agent_state")));

        PhaseGate productionRuntime = blocked.phaseGates().stream()
                .filter(gate -> gate.name().equals("Production Runtime"))
                .findFirst()
                .orElseThrow();

        assertThat(productionRuntime.status()).isEqualTo(PhaseGateStatus.BLOCKED);
        assertThat(productionRuntime.failureReasons())
                .containsExactly("Missing durable persistence table: ha_agent_state");
        assertThat(blocked.acceptanceReport().passed()).isFalse();
        assertThat(blocked.acceptanceReport().notes())
                .contains("Missing durable persistence table: ha_agent_state");
    }
}
