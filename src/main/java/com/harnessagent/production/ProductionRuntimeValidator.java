package com.harnessagent.production;

import org.springframework.stereotype.Component;

@Component
public class ProductionRuntimeValidator {

    private final ProductionRuntimeProperties properties;

    public ProductionRuntimeValidator(ProductionRuntimeProperties properties) {
        this.properties = properties;
    }

    public StateStorePlan stateStorePlan() {
        validate();
        ProductionRuntimeProperties.StateStore stateStore = properties.getStateStore();
        return switch (stateStore.getType()) {
            case LOCAL_JSON -> new StateStorePlan(
                    StateStoreType.LOCAL_JSON,
                    String.valueOf(stateStore.getLocalDirectory()),
                    false);
            case REDIS -> new StateStorePlan(StateStoreType.REDIS, stateStore.getRedisUri(), true);
            case MYSQL -> new StateStorePlan(StateStoreType.MYSQL, stateStore.getMysqlDsn(), true);
        };
    }

    public void validate() {
        if (properties.isProduction()
                && properties.getReplicaCount() > 1
                && properties.getStateStore().getType() == StateStoreType.LOCAL_JSON) {
            throw new IllegalStateException(
                    "Production multi-replica runtime must use Redis or MySQL distributed state.");
        }
        if (requiresSnapshotStore() && properties.getSnapshotStore().getType() == SnapshotStoreType.NONE) {
            throw new IllegalStateException("Sandbox workspace requires OSS, S3, MinIO, or JDBC snapshot store.");
        }
    }

    private boolean requiresSnapshotStore() {
        return properties.isProduction() && properties.getSandbox().isEnabled();
    }
}
