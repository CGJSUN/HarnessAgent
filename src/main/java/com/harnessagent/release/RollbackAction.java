package com.harnessagent.release;

public record RollbackAction(
        String capability,
        String action,
        String auditRequirement) {
}
