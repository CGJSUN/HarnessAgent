package com.harnessagent.production.telemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.harnessagent.production.infrastructure.InMemoryRuntimeTelemetry;

public interface RuntimeTelemetry {

    TelemetryEvent record(
            TelemetryEventType type,
            String ownerScopeId,
            String ownerId,
            String agentId,
            String component,
            Duration duration,
            Map<String, Object> attributes);

    List<TelemetryEvent> list(String ownerScopeId);

    static RuntimeTelemetry noop() {
        return new InMemoryRuntimeTelemetry(false);
    }
}
