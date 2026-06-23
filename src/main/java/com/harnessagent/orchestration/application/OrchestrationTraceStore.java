package com.harnessagent.orchestration.application;

import com.harnessagent.orchestration.domain.OrchestrationTrace;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

@Component
public class OrchestrationTraceStore {

    private final List<OrchestrationTrace> traces = new CopyOnWriteArrayList<>();

    public OrchestrationTrace save(OrchestrationTrace trace) {
        traces.add(trace);
        return trace;
    }

    public List<OrchestrationTrace> list(String tenantId) {
        return traces.stream()
                .filter(trace -> trace.tenantId().equals(tenantId))
                .toList();
    }
}
