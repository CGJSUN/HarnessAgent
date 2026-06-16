package com.harnessagent.api.request;

public record RagFeedbackRequest(
        String tenantId, String userId, String query, boolean helpful, String comment) {
}
