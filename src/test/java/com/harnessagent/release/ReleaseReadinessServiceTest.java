package com.harnessagent.release;

import static org.assertj.core.api.Assertions.assertThat;

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
}
