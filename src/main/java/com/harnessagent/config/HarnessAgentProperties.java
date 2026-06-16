package com.harnessagent.config;

import com.harnessagent.production.config.AgentWorkloadType;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "harness-agent")
public class HarnessAgentProperties {

    private String defaultAgentId = "enterprise-assistant";

    private String defaultProvider = "echo";

    private State state = new State();

    private Map<String, AgentDefinition> agents = new LinkedHashMap<>();

    private Map<String, ModelProviderDefinition> modelProviders = new LinkedHashMap<>();

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

    public static class AgentDefinition {
        private String name;
        private String systemPrompt;
        private String modelProvider;
        private String modelName;
        private String workspace;
        private AgentWorkloadType workloadType = AgentWorkloadType.OFFICE;
        private boolean compaction = true;
        private int maxIters = 3;

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

        public int getMaxIters() {
            return maxIters;
        }

        public void setMaxIters(int maxIters) {
            this.maxIters = maxIters;
        }
    }

    public static class ModelProviderDefinition {
        private String type;
        private String modelName;
        private String apiKey;
        private String apiKeyEnv;

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
    }
}
