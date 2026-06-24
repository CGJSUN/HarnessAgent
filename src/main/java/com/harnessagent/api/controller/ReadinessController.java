package com.harnessagent.api.controller;

import com.harnessagent.api.response.ScenarioResponse;
import com.harnessagent.release.EndToEndAcceptanceReport;
import com.harnessagent.release.ReadinessCheck;
import com.harnessagent.release.PersonalReadinessService;
import com.harnessagent.release.RollbackAction;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnostics/readiness")
public class ReadinessController {

    private final PersonalReadinessService personalReadinessService;

    public ReadinessController(PersonalReadinessService personalReadinessService) {
        this.personalReadinessService = personalReadinessService;
    }

    @GetMapping("/scenario")
    public ScenarioResponse scenario() {
        return new ScenarioResponse(
                personalReadinessService.mvpScenario(),
                personalReadinessService.mvpAcceptanceCriteria());
    }

    @GetMapping("/readiness-checks")
    public List<ReadinessCheck> readinessChecks() {
        return personalReadinessService.readinessChecks();
    }

    @GetMapping("/rollback")
    public List<RollbackAction> rollbackActions() {
        return personalReadinessService.rollbackActions();
    }

    @GetMapping("/acceptance")
    public EndToEndAcceptanceReport acceptance() {
        return personalReadinessService.acceptanceReport();
    }
}
