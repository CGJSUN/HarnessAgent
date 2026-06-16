package com.harnessagent.production.infrastructure;

import com.harnessagent.security.application.SensitiveDataRedactor;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.harnessagent.production.config.ProductionRuntimeProperties;
import com.harnessagent.production.telemetry.RuntimeTelemetry;
import com.harnessagent.production.telemetry.TelemetryEvent;
import com.harnessagent.production.telemetry.TelemetryEventType;

@Component
@Profile("!production")
public class InMemoryRuntimeTelemetry implements RuntimeTelemetry {

    private final boolean enabled;
    private final SensitiveDataRedactor redactor;
    private final List<TelemetryEvent> events = new CopyOnWriteArrayList<>();

    @Autowired
    public InMemoryRuntimeTelemetry(ProductionRuntimeProperties properties, SensitiveDataRedactor redactor) {
        this.enabled = properties.getObservability().isEnabled();
        this.redactor = redactor;
    }

    public InMemoryRuntimeTelemetry(boolean enabled) {
        this.enabled = enabled;
        this.redactor = new SensitiveDataRedactor();
    }

    @Override
    public TelemetryEvent record(
            TelemetryEventType type,
            String tenantId,
            String userId,
            String agentId,
            String component,
            Duration duration,
            Map<String, Object> attributes) {
        TelemetryEvent event = new TelemetryEvent(
                null,
                Instant.now(),
                type,
                tenantId,
                userId,
                agentId,
                component,
                duration == null ? 0 : duration.toMillis(),
                redactor.redactMap(attributes));
        if (enabled) {
            events.add(event);
        }
        return event;
    }

    @Override
    public List<TelemetryEvent> list(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return events.stream()
                .filter(event -> event.tenantId().equals(tenantId.trim()))
                .sorted(Comparator.comparing(TelemetryEvent::occurredAt))
                .toList();
    }
}
