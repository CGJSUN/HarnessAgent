package com.harnessagent.config;

import com.harnessagent.production.config.AgentWorkloadType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "harness-agent")
public class HarnessAgentProperties {

    private String defaultAgentId = "personal-assistant";

    private String defaultProvider = "echo";

    private State state = new State();

    private Map<String, AgentDefinition> agents = new LinkedHashMap<>();

    private Map<String, ModelProviderDefinition> modelProviders = new LinkedHashMap<>();

    private MemoryRag memoryRag = new MemoryRag();

    public String getDefaultAgentId() {
        return defaultAgentId;
    }

    public void setDefaultAgentId(String defaultAgentId) {
        this.defaultAgentId = defaultAgentId;
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Map<String, AgentDefinition> getAgents() {
        return agents;
    }

    public void setAgents(Map<String, AgentDefinition> agents) {
        this.agents = agents;
    }

    public Map<String, ModelProviderDefinition> getModelProviders() {
        return modelProviders;
    }

    public void setModelProviders(Map<String, ModelProviderDefinition> modelProviders) {
        this.modelProviders = modelProviders;
    }

    public MemoryRag getMemoryRag() {
        return memoryRag;
    }

    public void setMemoryRag(MemoryRag memoryRag) {
        this.memoryRag = memoryRag == null ? new MemoryRag() : memoryRag;
    }

    public AgentDefinition requireAgent(String agentId) {
        AgentDefinition definition = agents.get(agentId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown agent: " + agentId);
        }
        return definition;
    }

    public ModelProviderDefinition requireModelProvider(String providerId) {
        ModelProviderDefinition definition = modelProviders.get(providerId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown model provider: " + providerId);
        }
        return definition;
    }

    public static class State {
        private Path localDirectory = Path.of(".harness-agent/sessions");

        public Path getLocalDirectory() {
            return localDirectory;
        }

        public void setLocalDirectory(Path localDirectory) {
            this.localDirectory = localDirectory;
        }
    }

    public static class MemoryRag {
        private String provider = "local";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider == null || provider.isBlank() ? "local" : provider.trim();
        }
    }

    public static class AgentDefinition {
        private String name;
        private String systemPrompt;
        private String modelProvider;
        private String modelName;
        private String modelApiKeyRef;
        private String workspace;
        private AgentWorkloadType workloadType = AgentWorkloadType.OFFICE;
        private boolean compaction = true;
        private int compactionMessageThreshold = 24;
        private int maxIters = 3;
        private AgentBudget budget = new AgentBudget();
        private List<String> fallbackProviders = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public String getModelProvider() {
            return modelProvider;
        }

        public void setModelProvider(String modelProvider) {
            this.modelProvider = modelProvider;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getModelApiKeyRef() {
            return modelApiKeyRef;
        }

        public void setModelApiKeyRef(String modelApiKeyRef) {
            this.modelApiKeyRef = modelApiKeyRef;
        }

        public String getWorkspace() {
            return workspace;
        }

        public void setWorkspace(String workspace) {
            this.workspace = workspace;
        }

        public AgentWorkloadType getWorkloadType() {
            return workloadType;
        }

        public void setWorkloadType(AgentWorkloadType workloadType) {
            this.workloadType = workloadType == null ? AgentWorkloadType.OFFICE : workloadType;
        }

        public boolean isCompaction() {
            return compaction;
        }

        public void setCompaction(boolean compaction) {
            this.compaction = compaction;
        }

        public int getCompactionMessageThreshold() {
            return compactionMessageThreshold;
        }

        public void setCompactionMessageThreshold(int compactionMessageThreshold) {
            this.compactionMessageThreshold = compactionMessageThreshold <= 1 ? 2 : compactionMessageThreshold;
        }

        public int getMaxIters() {
            return maxIters;
        }

        public void setMaxIters(int maxIters) {
            this.maxIters = maxIters;
        }

        public AgentBudget getBudget() {
            return budget;
        }

        public void setBudget(AgentBudget budget) {
            this.budget = budget == null ? new AgentBudget() : budget;
        }

        public List<String> getFallbackProviders() {
            return fallbackProviders;
        }

        public void setFallbackProviders(List<String> fallbackProviders) {
            this.fallbackProviders = fallbackProviders == null ? new ArrayList<>() : fallbackProviders;
        }
    }

    public static class ModelProviderDefinition {
        private String type;
        private String modelName;
        private String apiKeyRef;
        private String apiKey;
        private String apiKeyEnv;
        private String baseUrl;
        private String endpointPath;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getApiKeyRef() {
            return apiKeyRef;
        }

        public void setApiKeyRef(String apiKeyRef) {
            this.apiKeyRef = apiKeyRef;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiKeyEnv() {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv) {
            this.apiKeyEnv = apiKeyEnv;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getEndpointPath() {
            return endpointPath;
        }

        public void setEndpointPath(String endpointPath) {
            this.endpointPath = endpointPath;
        }
    }

    public static class AgentBudget {
        private Long requestLimit;
        private Long tokenLimit;

        public Long getRequestLimit() {
            return requestLimit;
        }

        public void setRequestLimit(Long requestLimit) {
            this.requestLimit = requestLimit;
        }

        public Long getTokenLimit() {
            return tokenLimit;
        }

        public void setTokenLimit(Long tokenLimit) {
            this.tokenLimit = tokenLimit;
        }
    }
}
