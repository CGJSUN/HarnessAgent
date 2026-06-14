package com.harnessagent.production;

import java.time.Duration;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class RuntimeTimeoutGuard {

    private final ProductionRuntimeProperties properties;

    public RuntimeTimeoutGuard(ProductionRuntimeProperties properties) {
        this.properties = properties;
    }

    public <T> Mono<T> guardTool(Mono<T> work) {
        return guard(work, properties.getTimeouts().getToolTimeout(), "tool");
    }

    public <T> Mono<T> guardModel(Mono<T> work) {
        return guard(work, properties.getTimeouts().getModelTimeout(), "model");
    }

    public <T> Flux<T> guardStream(Flux<T> work) {
        return guard(work, properties.getTimeouts().getStreamTimeout(), "stream");
    }

    public <T> Mono<T> guardSandbox(Mono<T> work) {
        return guard(work, properties.getTimeouts().getSandboxTimeout(), "sandbox");
    }

    public <T> Mono<T> guard(Mono<T> work, Duration timeout, String label) {
        return work.timeout(timeout).onErrorMap(
                java.util.concurrent.TimeoutException.class,
                exception -> new IllegalStateException(label + " execution timed out", exception));
    }

    public <T> Flux<T> guard(Flux<T> work, Duration timeout, String label) {
        return work.timeout(timeout).onErrorMap(
                java.util.concurrent.TimeoutException.class,
                exception -> new IllegalStateException(label + " execution timed out", exception));
    }
}
