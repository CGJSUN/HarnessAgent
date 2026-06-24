package com.harnessagent.api.request;

public record RagFeedbackRequest(
        String ownerId, String query, boolean helpful, String comment) {
}
