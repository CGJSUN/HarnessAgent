package com.harnessagent.release;

import java.util.List;

public record PhaseGate(
        String name,
        PhaseGateStatus status,
        List<String> checks,
        String rollbackSwitch) {

    public PhaseGate {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }
}
