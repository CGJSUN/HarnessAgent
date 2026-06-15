package com.harnessagent.production;

import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProductionRuntimeValidator {

    private final ProductionRuntimeProperties properties;
    private final Supplier<DurablePersistenceHealth> capabilityHealth;

    public ProductionRuntimeValidator(ProductionRuntimeProperties properties) {
        this(properties, () -> DurablePersistenceHealth.healthy());
    }

    @Autowired
    public ProductionRuntimeValidator(
            ProductionRuntimeProperties properties,
            ObjectProvider<DurablePersistenceHealthService> healthService) {
        this(properties, () -> {
            DurablePersistenceHealthService service = healthService.getIfAvailable();
            return service == null ? DurablePersistenceHealth.healthy() : service.check();
        });
    }

    ProductionRuntimeValidator(
            ProductionRuntimeProperties properties,
            Supplier<DurablePersistenceHealth> capabilityHealth) {
        this.properties = properties;
        this.capabilityHealth = capabilityHealth;
    }

    public StateStorePlan stateStorePlan() {
        validate();
        ProductionRuntimeProperties.StateStore stateStore = properties.getStateStore();
        return switch (stateStore.getType()) {
            case LOCAL_JSON -> new StateStorePlan(
                    StateStoreType.LOCAL_JSON,
                    String.valueOf(stateStore.getLocalDirectory()),
                    false,
                    false);
            case REDIS -> new StateStorePlan(StateStoreType.REDIS, stateStore.getRedisUri(), true, properties.isProduction());
            case MYSQL -> new StateStorePlan(StateStoreType.MYSQL, stateStore.getMysqlDsn(), true, properties.isProduction());
        };
    }

    public void validate() {
        if (!properties.isProduction()) {
            return;
        }
        ProductionRuntimeProperties.StateStore stateStore = properties.getStateStore();
        if (stateStore.getType() == StateStoreType.LOCAL_JSON) {
            throw new IllegalStateException("Production runtime must use durable state; local JsonSession is not production durable.");
        }
        if (stateStore.getType() == StateStoreType.REDIS && isBlank(stateStore.getRedisUri())) {
            throw new IllegalStateException("Production Redis state store requires redis-uri.");
        }
        if (stateStore.getType() == StateStoreType.MYSQL && isBlank(stateStore.getMysqlDsn())) {
            throw new IllegalStateException("Production MySQL/JDBC state store requires mysql-dsn.");
        }
        if (!stateStore.isDurableImplementationWired()) {
            throw new IllegalStateException(
                    "Production durable state store is configured but not wired; local JsonSession is not allowed.");
        }
        if (stateStore.getType() == StateStoreType.MYSQL && missingSchemaMigrationInfo()) {
            throw new IllegalStateException(
                    "Production MySQL/JDBC durable state requires schema name and migration information.");
        }
        if (!telemetryConfigured()) {
            throw new IllegalStateException(
                    "Production telemetry requires OpenTelemetry export or durable telemetry store.");
        }
        if (requiresSnapshotStore() && properties.getSnapshotStore().getType() == SnapshotStoreType.NONE) {
            throw new IllegalStateException("Sandbox workspace requires OSS, S3, MinIO, or JDBC snapshot store.");
        }
        if (requiresSnapshotStore() && isBlank(properties.getSnapshotStore().getUri())) {
            throw new IllegalStateException("Sandbox workspace snapshot store requires a backend uri.");
        }
        if (requiresSnapshotStore() && !properties.getSnapshotStore().isImplementationWired()) {
            throw new IllegalStateException("Sandbox workspace snapshot store is configured but not wired.");
        }
    }

    public void validateCapabilities() {
        validate();
        if (!properties.isProduction() || !properties.getHealthCheck().isEnabled()) {
            return;
        }
        DurablePersistenceHealth health = capabilityHealth.get();
        if (health != null && !health.passed()) {
            throw new IllegalStateException(
                    "Production durable persistence capabilities failed: "
                            + String.join("; ", health.failureReasons()));
        }
    }

    private boolean requiresSnapshotStore() {
        return properties.isProduction() && properties.getSandbox().isEnabled();
    }

    private boolean missingSchemaMigrationInfo() {
        ProductionRuntimeProperties.Schema schema = properties.getSchema();
        return isBlank(schema.getName())
                || isBlank(schema.getMigrationTool())
                || isBlank(schema.getMigrationLocation());
    }

    private boolean telemetryConfigured() {
        ProductionRuntimeProperties.Telemetry telemetry = properties.getTelemetry();
        return telemetry.isOpenTelemetryExportEnabled() || telemetry.isDurableStoreEnabled();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
