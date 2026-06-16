package com.harnessagent.production.budget;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.harnessagent.production.config.ProductionRuntimeProperties;

@Component
public class BudgetLimiter {

    private final ProductionRuntimeProperties properties;
    private final BudgetCounterStore counterStore;

    @Autowired
    public BudgetLimiter(ProductionRuntimeProperties properties, BudgetCounterStore counterStore) {
        this.properties = properties;
        this.counterStore = counterStore;
    }

    public BudgetDecision tryConsume(BudgetScope scope, long tokens) {
        BudgetCounter fullScope = null;
        for (String key : keys(scope)) {
            BudgetCounter counter = counterStore.increment(key, tokens);
            if (counter.requests() > properties.getBudget().getRequestLimit()) {
                return new BudgetDecision(false, key + ":request_limit_exceeded", counter.requests(), counter.tokens());
            }
            if (counter.tokens() > properties.getBudget().getTokenLimit()) {
                return new BudgetDecision(false, key + ":token_budget_exceeded", counter.requests(), counter.tokens());
            }
            if (key.equals("scope:" + scope.key())) {
                fullScope = counter;
            }
        }
        return new BudgetDecision(true, "", fullScope.requests(), fullScope.tokens());
    }

    private static List<String> keys(BudgetScope scope) {
        return List.of(
                "tenant:" + scope.tenantId(),
                "user:" + scope.tenantId() + ":" + scope.userId(),
                "agent:" + scope.tenantId() + ":" + scope.agentId(),
                "provider:" + scope.providerId(),
                "scope:" + scope.key());
    }
}
