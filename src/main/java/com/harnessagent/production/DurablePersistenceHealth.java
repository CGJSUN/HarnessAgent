package com.harnessagent.production;

import java.util.List;

public record DurablePersistenceHealth(
        boolean passed,
        List<String> failureReasons) {

    public DurablePersistenceHealth {
        failureReasons = failureReasons == null ? List.of() : List.copyOf(failureReasons);
    }

    public static DurablePersistenceHealth healthy() {
        return new DurablePersistenceHealth(true, List.of());
    }

    public static DurablePersistenceHealth failed(List<String> failureReasons) {
        return new DurablePersistenceHealth(false, failureReasons);
    }
}
