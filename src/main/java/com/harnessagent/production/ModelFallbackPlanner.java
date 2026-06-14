package com.harnessagent.production;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ModelFallbackPlanner {

    private final ProductionRuntimeProperties properties;

    public ModelFallbackPlanner(ProductionRuntimeProperties properties) {
        this.properties = properties;
    }

    public List<String> fallbackProviders(String primaryProvider, Throwable failure) {
        if (!isRetryable(failure)) {
            return List.of();
        }
        return properties.getFallback().getProviders()
                .getOrDefault(primaryProvider, List.of());
    }

    public boolean isRetryable(Throwable failure) {
        if (failure instanceof RetryableModelException retryable) {
            return properties.getFallback().getRetryableStatusCodes().contains(retryable.statusCode());
        }
        return failure instanceof java.io.IOException
                || failure instanceof java.net.SocketTimeoutException
                || failure instanceof java.util.concurrent.TimeoutException;
    }
}
