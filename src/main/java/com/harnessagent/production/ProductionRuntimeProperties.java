package com.harnessagent.production;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "harness-agent.production")
public class ProductionRuntimeProperties {

    private RuntimeProfile profile = RuntimeProfile.DEVELOPMENT;
    private int replicaCount = 1;
    private StateStore stateStore = new StateStore();
    private RemoteFilesystem remoteFilesystem = new RemoteFilesystem();
    private Sandbox sandbox = new Sandbox();
    private SnapshotStore snapshotStore = new SnapshotStore();
    private Observability observability = new Observability();
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
        private String image = "harness-agent-sandbox:latest";
        private String workspaceRoot = "/workspace";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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
            this.workspaceRoot = workspaceRoot;
        }
    }

    public static class SnapshotStore {
        private SnapshotStoreType type = SnapshotStoreType.NONE;
        private String uri = "";

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
