package com.harnessagent.production;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class BudgetLimiter {

    private final ProductionRuntimeProperties properties;
    private final Map<String, UsageCounter> counters = new ConcurrentHashMap<>();

    public BudgetLimiter(ProductionRuntimeProperties properties) {
        this.properties = properties;
    }

    public BudgetDecision tryConsume(BudgetScope scope, long tokens) {
        for (String key : keys(scope)) {
            UsageCounter counter = counters.computeIfAbsent(key, ignored -> new UsageCounter());
            long nextRequests = counter.requests.incrementAndGet();
            long nextTokens = counter.tokens.addAndGet(Math.max(tokens, 0));
            if (nextRequests > properties.getBudget().getRequestLimit()) {
                return new BudgetDecision(false, key + ":request_limit_exceeded", nextRequests, nextTokens);
            }
            if (nextTokens > properties.getBudget().getTokenLimit()) {
                return new BudgetDecision(false, key + ":token_budget_exceeded", nextRequests, nextTokens);
            }
        }
        UsageCounter fullScope = counters.get("scope:" + scope.key());
        return new BudgetDecision(true, "", fullScope.requests.get(), fullScope.tokens.get());
    }

    private static List<String> keys(BudgetScope scope) {
        return List.of(
                "tenant:" + scope.tenantId(),
                "user:" + scope.tenantId() + ":" + scope.userId(),
                "agent:" + scope.tenantId() + ":" + scope.agentId(),
                "provider:" + scope.providerId(),
                "scope:" + scope.key());
    }

    private static class UsageCounter {
        private final AtomicLong requests = new AtomicLong();
        private final AtomicLong tokens = new AtomicLong();
    }
}
