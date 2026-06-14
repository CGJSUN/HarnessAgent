package com.harnessagent.release;

import java.util.List;

public record EndToEndAcceptanceReport(
        boolean tenantIsolation,
        boolean permissionFiltering,
        boolean highRiskConfirmation,
        boolean auditTraceability,
        boolean operationalObservability,
        List<String> notes) {

    public boolean passed() {
        return tenantIsolation
                && permissionFiltering
                && highRiskConfirmation
                && auditTraceability
                && operationalObservability;
    }
}
