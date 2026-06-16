package com.harnessagent.agent.runtime;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AgentRuntime {

    Mono<AgentReply> complete(AgentRunRequest request);

    Flux<AgentRuntimeEvent> stream(AgentRunRequest request);
}
