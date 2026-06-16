package com.harnessagent.production.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.harnessagent.production.sandbox.SandboxExecutionMode;
import com.harnessagent.production.snapshot.SnapshotStore;
import com.harnessagent.production.snapshot.SnapshotStoreType;
import com.harnessagent.production.state.StateStoreType;

@ConfigurationProperties(prefix = "harness-agent.production")
public class ProductionRuntimeProperties {

    private RuntimeProfile profile = RuntimeProfile.DEVELOPMENT;
    private int replicaCount = 1;
    private StateStore stateStore = new StateStore();
    private DurableStores durableStores = new DurableStores();
    private Schema schema = new Schema();
    private RemoteFilesystem remoteFilesystem = new RemoteFilesystem();
    private Sandbox sandbox = new Sandbox();
    private SnapshotStore snapshotStore = new SnapshotStore();
    private Observability observability = new Observability();
    private Telemetry telemetry = new Telemetry();
    private HealthCheck healthCheck = new HealthCheck();
    private Retention retention = new Retention();
    private Budget budget = new Budget();
    private Fallback fallback = new Fallback();
    private Timeouts timeouts = new Timeouts();

    public RuntimeProfile getProfile() {
        return profile;
    }

    public void setProfile(RuntimeProfile profile) {
        this.profile = profile == null ? RuntimeProfile.DEVELOPMENT : profile;
    }

    public int getReplicaCount() {
        return replicaCount;
    }

    public void setReplicaCount(int replicaCount) {
        this.replicaCount = replicaCount;
    }

    public StateStore getStateStore() {
        return stateStore;
    }

    public void setStateStore(StateStore stateStore) {
        this.stateStore = stateStore == null ? new StateStore() : stateStore;
    }

    public DurableStores getDurableStores() {
        return durableStores;
    }

    public void setDurableStores(DurableStores durableStores) {
        this.durableStores = durableStores == null ? new DurableStores() : durableStores;
    }

    public Schema getSchema() {
        return schema;
    }

    public void setSchema(Schema schema) {
        this.schema = schema == null ? new Schema() : schema;
    }

    public RemoteFilesystem getRemoteFilesystem() {
        return remoteFilesystem;
    }

    public void setRemoteFilesystem(RemoteFilesystem remoteFilesystem) {
        this.remoteFilesystem = remoteFilesystem == null ? new RemoteFilesystem() : remoteFilesystem;
    }

    public Sandbox getSandbox() {
        return sandbox;
    }

    public void setSandbox(Sandbox sandbox) {
        this.sandbox = sandbox == null ? new Sandbox() : sandbox;
    }

    public SnapshotStore getSnapshotStore() {
        return snapshotStore;
    }

    public void setSnapshotStore(SnapshotStore snapshotStore) {
        this.snapshotStore = snapshotStore == null ? new SnapshotStore() : snapshotStore;
    }

    public Observability getObservability() {
        return observability;
    }

    public void setObservability(Observability observability) {
        this.observability = observability == null ? new Observability() : observability;
    }

    public Telemetry getTelemetry() {
        return telemetry;
    }

    public void setTelemetry(Telemetry telemetry) {
        this.telemetry = telemetry == null ? new Telemetry() : telemetry;
    }

    public HealthCheck getHealthCheck() {
        return healthCheck;
    }

    public void setHealthCheck(HealthCheck healthCheck) {
        this.healthCheck = healthCheck == null ? new HealthCheck() : healthCheck;
    }

    public Retention getRetention() {
        return retention;
    }

    public void setRetention(Retention retention) {
        this.retention = retention == null ? new Retention() : retention;
    }

    public Budget getBudget() {
        return budget;
    }

    public void setBudget(Budget budget) {
        this.budget = budget == null ? new Budget() : budget;
    }

    public Fallback getFallback() {
        return fallback;
    }

    public void setFallback(Fallback fallback) {
        this.fallback = fallback == null ? new Fallback() : fallback;
    }

    public Timeouts getTimeouts() {
        return timeouts;
    }

    public void setTimeouts(Timeouts timeouts) {
        this.timeouts = timeouts == null ? new Timeouts() : timeouts;
    }

    public boolean isProduction() {
        return profile == RuntimeProfile.PRODUCTION;
    }

    public static class StateStore {
        private StateStoreType type = StateStoreType.LOCAL_JSON;
        private Path localDirectory = Path.of(".harness-agent/sessions");
        private String redisUri;
        private String mysqlDsn;
        private boolean durableImplementationWired;
        private String implementationName = "";

        public StateStoreType getType() {
            return type;
        }

        public void setType(StateStoreType type) {
            this.type = type == null ? StateStoreType.LOCAL_JSON : type;
        }

        public Path getLocalDirectory() {
            return localDirectory;
        }

        public void setLocalDirectory(Path localDirectory) {
            this.localDirectory = localDirectory;
        }

        public String getRedisUri() {
            return redisUri;
        }

        public void setRedisUri(String redisUri) {
            this.redisUri = redisUri;
        }

        public String getMysqlDsn() {
            return mysqlDsn;
        }

        public void setMysqlDsn(String mysqlDsn) {
            this.mysqlDsn = mysqlDsn;
        }

        public boolean isDurableImplementationWired() {
            return durableImplementationWired;
        }

        public void setDurableImplementationWired(boolean durableImplementationWired) {
            this.durableImplementationWired = durableImplementationWired;
        }

        public String getImplementationName() {
            return implementationName;
        }

        public void setImplementationName(String implementationName) {
            this.implementationName = implementationName == null ? "" : implementationName;
        }
    }

    public static class DurableStores {
        private StateStoreType session = StateStoreType.LOCAL_JSON;
        private StateStoreType knowledge = StateStoreType.LOCAL_JSON;
        private StateStoreType tool = StateStoreType.LOCAL_JSON;
        private StateStoreType audit = StateStoreType.LOCAL_JSON;
        private StateStoreType telemetry = StateStoreType.LOCAL_JSON;
        private StateStoreType budgetCounter = StateStoreType.LOCAL_JSON;

        public StateStoreType getSession() {
            return session;
        }

        public void setSession(StateStoreType session) {
            this.session = session == null ? StateStoreType.LOCAL_JSON : session;
        }

        public StateStoreType getKnowledge() {
            return knowledge;
        }

        public void setKnowledge(StateStoreType knowledge) {
            this.knowledge = knowledge == null ? StateStoreType.LOCAL_JSON : knowledge;
        }

        public StateStoreType getTool() {
            return tool;
        }

        public void setTool(StateStoreType tool) {
            this.tool = tool == null ? StateStoreType.LOCAL_JSON : tool;
        }

        public StateStoreType getAudit() {
            return audit;
        }

        public void setAudit(StateStoreType audit) {
            this.audit = audit == null ? StateStoreType.LOCAL_JSON : audit;
        }

        public StateStoreType getTelemetry() {
            return telemetry;
        }

        public void setTelemetry(StateStoreType telemetry) {
            this.telemetry = telemetry == null ? StateStoreType.LOCAL_JSON : telemetry;
        }

        public StateStoreType getBudgetCounter() {
            return budgetCounter;
        }

        public void setBudgetCounter(StateStoreType budgetCounter) {
            this.budgetCounter = budgetCounter == null ? StateStoreType.LOCAL_JSON : budgetCounter;
        }
    }

    public static class Schema {
        private String name = "";
        private String migrationTool = "";
        private String migrationLocation = "";
        private String version = "";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name == null ? "" : name;
        }

        public String getMigrationTool() {
            return migrationTool;
        }

        public void setMigrationTool(String migrationTool) {
            this.migrationTool = migrationTool == null ? "" : migrationTool;
        }

        public String getMigrationLocation() {
            return migrationLocation;
        }

        public void setMigrationLocation(String migrationLocation) {
            this.migrationLocation = migrationLocation == null ? "" : migrationLocation;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version == null ? "" : version;
        }
    }

    public static class RemoteFilesystem {
        private String rootUri = "s3://harness-agent-workspaces";

        public String getRootUri() {
            return rootUri;
        }

        public void setRootUri(String rootUri) {
            this.rootUri = rootUri;
        }
    }

    public static class Sandbox {
        private boolean enabled;
        private SandboxExecutionMode mode = SandboxExecutionMode.DOCKER;
        private String image = "harness-agent-sandbox:latest";
        private String workspaceRoot = "/workspace";
        private String remoteEndpoint = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public SandboxExecutionMode getMode() {
            return mode;
        }

        public void setMode(SandboxExecutionMode mode) {
            this.mode = mode == null ? SandboxExecutionMode.DOCKER : mode;
        }

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public String getWorkspaceRoot() {
            return workspaceRoot;
        }

        public void setWorkspaceRoot(String workspaceRoot) {
            String value = workspaceRoot == null || workspaceRoot.isBlank() ? "/workspace" : workspaceRoot.trim();
            if (!Path.of(value).isAbsolute()) {
                throw new IllegalArgumentException("sandbox workspace root must be an absolute path");
            }
            this.workspaceRoot = value;
        }

        public String getRemoteEndpoint() {
            return remoteEndpoint;
        }

        public void setRemoteEndpoint(String remoteEndpoint) {
            this.remoteEndpoint = remoteEndpoint == null ? "" : remoteEndpoint.trim();
        }
    }

    public static class SnapshotStore {
        private SnapshotStoreType type = SnapshotStoreType.NONE;
        private String uri = "";
        private boolean implementationWired;
        private boolean writableCheckEnabled = true;

        public SnapshotStoreType getType() {
            return type;
        }

        public void setType(SnapshotStoreType type) {
            this.type = type == null ? SnapshotStoreType.NONE : type;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public boolean isImplementationWired() {
            return implementationWired;
        }

        public void setImplementationWired(boolean implementationWired) {
            this.implementationWired = implementationWired;
        }

        public boolean isWritableCheckEnabled() {
            return writableCheckEnabled;
        }

        public void setWritableCheckEnabled(boolean writableCheckEnabled) {
            this.writableCheckEnabled = writableCheckEnabled;
        }
    }

    public static class Observability {
        private boolean enabled = true;
        private String serviceName = "harness-agent";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }
    }

    public static class Telemetry {
        private boolean openTelemetryExportEnabled;
        private String otlpEndpoint = "";
        private boolean durableStoreEnabled;
        private StateStoreType durableStoreType = StateStoreType.MYSQL;

        public boolean isOpenTelemetryExportEnabled() {
            return openTelemetryExportEnabled;
        }

        public void setOpenTelemetryExportEnabled(boolean openTelemetryExportEnabled) {
            this.openTelemetryExportEnabled = openTelemetryExportEnabled;
        }

        public String getOtlpEndpoint() {
            return otlpEndpoint;
        }

        public void setOtlpEndpoint(String otlpEndpoint) {
            this.otlpEndpoint = otlpEndpoint == null ? "" : otlpEndpoint;
        }

        public boolean isDurableStoreEnabled() {
            return durableStoreEnabled;
        }

        public void setDurableStoreEnabled(boolean durableStoreEnabled) {
            this.durableStoreEnabled = durableStoreEnabled;
        }

        public StateStoreType getDurableStoreType() {
            return durableStoreType;
        }

        public void setDurableStoreType(StateStoreType durableStoreType) {
            this.durableStoreType = durableStoreType == null ? StateStoreType.MYSQL : durableStoreType;
        }
    }

    public static class HealthCheck {
        private boolean enabled = true;
        private boolean failFast = true;
        private Duration timeout = Duration.ofSeconds(5);
        private boolean includeFailureReasons = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFailFast() {
            return failFast;
        }

        public void setFailFast(boolean failFast) {
            this.failFast = failFast;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public boolean isIncludeFailureReasons() {
            return includeFailureReasons;
        }

        public void setIncludeFailureReasons(boolean includeFailureReasons) {
            this.includeFailureReasons = includeFailureReasons;
        }
    }

    public static class Retention {
        private Duration audit = Duration.ofDays(180);
        private Duration telemetry = Duration.ofDays(30);
        private Duration snapshots = Duration.ofDays(14);

        public Duration getAudit() {
            return audit;
        }

        public void setAudit(Duration audit) {
            this.audit = audit;
        }

        public Duration getTelemetry() {
            return telemetry;
        }

        public void setTelemetry(Duration telemetry) {
            this.telemetry = telemetry;
        }

        public Duration getSnapshots() {
            return snapshots;
        }

        public void setSnapshots(Duration snapshots) {
            this.snapshots = snapshots;
        }
    }

    public static class Budget {
        private long requestLimit = 1000;
        private long tokenLimit = 1_000_000;

        public long getRequestLimit() {
            return requestLimit;
        }

        public void setRequestLimit(long requestLimit) {
            this.requestLimit = requestLimit;
        }

        public long getTokenLimit() {
            return tokenLimit;
        }

        public void setTokenLimit(long tokenLimit) {
            this.tokenLimit = tokenLimit;
        }
    }

    public static class Fallback {
        private Map<String, List<String>> providers = new LinkedHashMap<>();
        private List<Integer> retryableStatusCodes = List.of(429, 500, 502, 503, 504);

        public Map<String, List<String>> getProviders() {
            return providers;
        }

        public void setProviders(Map<String, List<String>> providers) {
            this.providers = providers == null ? new LinkedHashMap<>() : providers;
        }

        public List<Integer> getRetryableStatusCodes() {
            return retryableStatusCodes;
        }

        public void setRetryableStatusCodes(List<Integer> retryableStatusCodes) {
            this.retryableStatusCodes = retryableStatusCodes == null ? List.of() : retryableStatusCodes;
        }
    }

    public static class Timeouts {
        private Duration modelTimeout = Duration.ofMinutes(2);
        private Duration streamTimeout = Duration.ofMinutes(5);
        private Duration toolTimeout = Duration.ofSeconds(30);
        private Duration sandboxTimeout = Duration.ofMinutes(10);

        public Duration getModelTimeout() {
            return modelTimeout;
        }

        public void setModelTimeout(Duration modelTimeout) {
            this.modelTimeout = modelTimeout;
        }

        public Duration getStreamTimeout() {
            return streamTimeout;
        }

        public void setStreamTimeout(Duration streamTimeout) {
            this.streamTimeout = streamTimeout;
        }

        public Duration getToolTimeout() {
            return toolTimeout;
        }

        public void setToolTimeout(Duration toolTimeout) {
            this.toolTimeout = toolTimeout;
        }

        public Duration getSandboxTimeout() {
            return sandboxTimeout;
        }

        public void setSandboxTimeout(Duration sandboxTimeout) {
            this.sandboxTimeout = sandboxTimeout;
        }
    }
}
