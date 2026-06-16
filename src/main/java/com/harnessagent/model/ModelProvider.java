package com.harnessagent.model;

import io.agentscope.core.model.Model;

public interface ModelProvider {

    String id();

    Model createModel(String requestedModelName);

    default Model createModel(ModelProviderRequest request) {
        return createModel(request == null ? null : request.modelName());
    }
}
