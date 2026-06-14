package com.harnessagent.agent;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.Model;
import java.nio.file.Path;

public final class EnterpriseHarnessAgent {

    private final ReActAgent delegate;
    private final Path workspace;
    private final boolean compactionEnabled;

    private EnterpriseHarnessAgent(Builder builder) {
        this.delegate = ReActAgent.builder()
                .name(builder.name)
                .sysPrompt(builder.systemPrompt)
                .model(builder.model)
                .memory(new InMemoryMemory())
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

        public EnterpriseHarnessAgent build() {
            if (model == null) {
                throw new IllegalArgumentException("model is required");
            }
            return new EnterpriseHarnessAgent(this);
        }
    }
}
