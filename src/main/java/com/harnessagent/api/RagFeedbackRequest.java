package com.harnessagent.api;

public record RagFeedbackRequest(
        String tenantId, String userId, String query, boolean helpful, String comment) {
}
