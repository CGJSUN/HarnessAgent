package com.harnessagent.production.infrastructure;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.harnessagent.production.config.ProductionRuntimeProperties;

@Component
public class ModelFallbackPlanner {

    private static final Logger log = LoggerFactory.getLogger(ModelFallbackPlanner.class);

    private final ProductionRuntimeProperties properties;

    public ModelFallbackPlanner(ProductionRuntimeProperties properties) {
        this.properties = properties;
    }

    public List<String> fallbackProviders(String primaryProvider, Throwable failure) {
        if (!isRetryable(failure)) {
            return List.of();
        }
        List<String> providers = properties.getFallback().getProviders()
                .getOrDefault(primaryProvider, List.of());
        if (!providers.isEmpty()) {
            // Fallback is only selected for retryable model failures; logs carry routing metadata, not prompts or errors.
            log.warn(
                    "model fallback triggered primaryProvider={} fallbackCount={} errorType={} statusCode={}",
                    safeProvider(primaryProvider),
                    providers.size(),
                    failure == null ? "unknown" : failure.getClass().getSimpleName(),
                    statusCode(failure));
        }
        return providers;
    }

    public boolean isRetryable(Throwable failure) {
        if (failure instanceof RetryableModelException retryable) {
            return properties.getFallback().getRetryableStatusCodes().contains(retryable.statusCode());
        }
        return failure instanceof java.io.IOException
                || failure instanceof java.net.SocketTimeoutException
                || failure instanceof java.util.concurrent.TimeoutException;
    }

    private static String safeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "default";
        }
        String normalized = provider.trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
        return normalized.isBlank() ? "default" : normalized;
    }

    private static String statusCode(Throwable failure) {
        return failure instanceof RetryableModelException retryable
                ? String.valueOf(retryable.statusCode())
                : "n/a";
    }
}
