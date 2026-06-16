package com.harnessagent.api.controller;

import com.harnessagent.api.response.ScenarioResponse;
import com.harnessagent.release.EndToEndAcceptanceReport;
import com.harnessagent.release.PhaseGate;
import com.harnessagent.release.ReleaseReadinessService;
import com.harnessagent.release.RollbackAction;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/release")
public class ReleaseController {

    private final ReleaseReadinessService releaseReadinessService;

    public ReleaseController(ReleaseReadinessService releaseReadinessService) {
        this.releaseReadinessService = releaseReadinessService;
    }

    @GetMapping("/scenario")
    public ScenarioResponse scenario() {
        return new ScenarioResponse(
                releaseReadinessService.mvpScenario(),
                releaseReadinessService.mvpAcceptanceCriteria());
    }

    @GetMapping("/phase-gates")
    public List<PhaseGate> phaseGates() {
        return releaseReadinessService.phaseGates();
    }

    @GetMapping("/rollback")
    public List<RollbackAction> rollbackActions() {
        return releaseReadinessService.rollbackActions();
    }

    @GetMapping("/acceptance")
    public EndToEndAcceptanceReport acceptance() {
        return releaseReadinessService.acceptanceReport();
    }
}
