package com.harnessagent.model;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;

public class EchoModel implements Model {

    private final String modelName;

    public EchoModel(String modelName) {
        this.modelName = modelName;
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        String lastUserMessage = messages == null
                ? ""
                : messages.stream()
                        .filter(message -> message.getRole() != null)
                        .reduce((first, second) -> second)
                        .map(Msg::getTextContent)
                        .orElse("");
        String answer = "Echo: " + lastUserMessage;
        return Flux.just(ChatResponse.builder()
                .content(List.of(TextBlock.builder().text(answer).build()))
                .metadata(Map.of("provider", "echo"))
                .finishReason("stop")
                .build());
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}
