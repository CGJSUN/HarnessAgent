package com.harnessagent.production.infrastructure;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.harnessagent.production.config.ProductionRuntimeProperties;

@Component
public class RuntimeTimeoutGuard {

    private static final Logger log = LoggerFactory.getLogger(RuntimeTimeoutGuard.class);

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
                exception -> {
                    log.warn("runtime timeout reason={} timeoutMillis={}", label, timeout.toMillis());
                    return new IllegalStateException(label + " execution timed out", exception);
                });
    }

    public <T> Flux<T> guard(Flux<T> work, Duration timeout, String label) {
        return work.timeout(timeout).onErrorMap(
                java.util.concurrent.TimeoutException.class,
                exception -> {
                    log.warn("runtime timeout reason={} timeoutMillis={}", label, timeout.toMillis());
                    return new IllegalStateException(label + " execution timed out", exception);
                });
    }
}
