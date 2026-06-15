package com.harnessagent.release;

import java.util.List;

public record PhaseGate(
        String name,
        PhaseGateStatus status,
        List<String> checks,
        String rollbackSwitch,
        List<String> failureReasons) {

    public PhaseGate(String name, PhaseGateStatus status, List<String> checks, String rollbackSwitch) {
        this(name, status, checks, rollbackSwitch, List.of());
    }

    public PhaseGate {
        checks = checks == null ? List.of() : List.copyOf(checks);
        failureReasons = failureReasons == null ? List.of() : List.copyOf(failureReasons);
    }
}
