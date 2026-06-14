package com.harnessagent.console;

import java.time.Instant;

public record AuditSearchFilter(
        String userId,
        String sessionId,
        String resourceId,
        String action,
        Instant from,
        Instant to) {

    public boolean matches(String userId, String sessionId, String resourceId, String action, Instant occurredAt) {
        return matchesText(this.userId, userId)
                && matchesText(this.sessionId, sessionId)
                && matchesText(this.resourceId, resourceId)
                && matchesText(this.action, action)
                && (from == null || !occurredAt.isBefore(from))
                && (to == null || !occurredAt.isAfter(to));
    }

    private static boolean matchesText(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }
}
