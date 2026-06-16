package com.harnessagent.model;

public record ModelProviderRequest(
        String providerId,
        String modelName,
        String apiKeyRef) {
}
