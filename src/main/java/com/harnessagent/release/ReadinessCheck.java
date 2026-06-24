package com.harnessagent.release;

import java.util.List;

public record ReadinessCheck(
        String name,
        ReadinessStatus status,
        List<String> checks,
        String rollbackSwitch,
        List<String> failureReasons) {

    public ReadinessCheck(String name, ReadinessStatus status, List<String> checks, String rollbackSwitch) {
        this(name, status, checks, rollbackSwitch, List.of());
    }

    public ReadinessCheck {
        checks = checks == null ? List.of() : List.copyOf(checks);
        failureReasons = failureReasons == null ? List.of() : List.copyOf(failureReasons);
    }
}
