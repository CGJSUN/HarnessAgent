package com.harnessagent.release;

import static org.assertj.core.api.Assertions.assertThat;

import com.harnessagent.production.health.DurablePersistenceHealth;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalReadinessServiceTest {

    private final PersonalReadinessService service = new PersonalReadinessService();

    @Test
    void exposesMvpScenarioAndReadinessChecks() {
        assertThat(service.mvpScenario()).isEqualTo("个人 Agent 工作台");
        assertThat(service.mvpAcceptanceCriteria()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(service.readinessChecks())
                .extracting(ReadinessCheck::status)
                .containsOnly(ReadinessStatus.PASSED);
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
        PersonalReadinessService blocked = new PersonalReadinessService(
                () -> DurablePersistenceHealth.failed(List.of("Missing durable persistence table: ha_agent_state")));

        ReadinessCheck productionRuntime = blocked.readinessChecks().stream()
                .filter(gate -> gate.name().equals("Production Runtime"))
                .findFirst()
                .orElseThrow();

        assertThat(productionRuntime.status()).isEqualTo(ReadinessStatus.BLOCKED);
        assertThat(productionRuntime.failureReasons())
                .containsExactly("Missing durable persistence table: ha_agent_state");
        assertThat(blocked.acceptanceReport().passed()).isFalse();
        assertThat(blocked.acceptanceReport().notes())
                .contains("Missing durable persistence table: ha_agent_state");
    }
}
