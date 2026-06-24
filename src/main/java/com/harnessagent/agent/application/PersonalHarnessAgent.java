package com.harnessagent.agent.application;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import java.nio.file.Path;

public final class PersonalHarnessAgent {

    private final ReActAgent delegate;
    private final Path workspace;
    private final boolean compactionEnabled;

    private PersonalHarnessAgent(Builder builder) {
        this.delegate = ReActAgent.builder()
                .name(builder.name)
                .sysPrompt(builder.systemPrompt)
                .model(builder.model)
                .stateStore(builder.stateStore)
                .defaultSessionId(builder.defaultSessionId)
                .maxIters(builder.maxIters)
                .build();
        this.workspace = builder.workspace;
        this.compactionEnabled = builder.compactionEnabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ReActAgent delegate() {
        return delegate;
    }

    public Path workspace() {
        return workspace;
    }

    public boolean compactionEnabled() {
        return compactionEnabled;
    }

    public static final class Builder {
        private String name;
        private String systemPrompt;
        private Model model;
        private AgentStateStore stateStore;
        private String defaultSessionId = "default";
        private Path workspace = Path.of(".harness-agent/workspaces/default");
        private boolean compactionEnabled = true;
        private int maxIters = 3;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder model(Model model) {
            this.model = model;
            return this;
        }

        public Builder stateStore(AgentStateStore stateStore) {
            this.stateStore = stateStore;
            return this;
        }

        public Builder defaultSessionId(String defaultSessionId) {
            this.defaultSessionId = defaultSessionId == null || defaultSessionId.isBlank()
                    ? this.defaultSessionId
                    : defaultSessionId.trim();
            return this;
        }

        public Builder workspace(Path workspace) {
            this.workspace = workspace == null ? this.workspace : workspace;
            return this;
        }

        public Builder compactionEnabled(boolean compactionEnabled) {
            this.compactionEnabled = compactionEnabled;
            return this;
        }

        public Builder maxIters(int maxIters) {
            this.maxIters = maxIters;
            return this;
        }

        public PersonalHarnessAgent build() {
            if (model == null) {
                throw new IllegalArgumentException("model is required");
            }
            if (stateStore == null) {
                throw new IllegalArgumentException("stateStore is required");
            }
            return new PersonalHarnessAgent(this);
        }
    }
}
