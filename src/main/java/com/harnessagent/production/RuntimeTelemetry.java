package com.harnessagent.production;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface RuntimeTelemetry {

    TelemetryEvent record(
            TelemetryEventType type,
            String tenantId,
            String userId,
            String agentId,
            String component,
            Duration duration,
            Map<String, Object> attributes);

    List<TelemetryEvent> list(String tenantId);

    static RuntimeTelemetry noop() {
        return new InMemoryRuntimeTelemetry(false);
    }
}
