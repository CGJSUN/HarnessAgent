package com.harnessagent.model;

import org.springframework.stereotype.Component;

@Component
public class EchoModelProvider implements ModelProvider {

    @Override
    public String id() {
        return "echo";
    }

    @Override
    public EchoModel createModel(String requestedModelName) {
        return new EchoModel(requestedModelName == null || requestedModelName.isBlank()
                ? "echo-local"
                : requestedModelName);
    }
}
