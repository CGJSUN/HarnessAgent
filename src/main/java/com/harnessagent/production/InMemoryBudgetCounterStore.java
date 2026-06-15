package com.harnessagent.production;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!production")
public class InMemoryBudgetCounterStore implements BudgetCounterStore {

    private final Map<String, UsageCounter> counters = new ConcurrentHashMap<>();

    @Override
    public BudgetCounter increment(String key, long tokens) {
        UsageCounter counter = counters.computeIfAbsent(key, ignored -> new UsageCounter());
        long nextRequests = counter.requests.incrementAndGet();
        long nextTokens = counter.tokens.addAndGet(Math.max(tokens, 0));
        return new BudgetCounter(key, nextRequests, nextTokens);
    }

    private static class UsageCounter {
        private final AtomicLong requests = new AtomicLong();
        private final AtomicLong tokens = new AtomicLong();
    }
}
