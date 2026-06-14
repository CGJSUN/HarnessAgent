package com.harnessagent.model;

import io.agentscope.core.model.Model;

public interface ModelProvider {

    String id();

    Model createModel(String requestedModelName);
}
