package com.harnessagent.model;

import com.harnessagent.production.budget.BudgetLimit;
import java.util.List;

public record ModelSelection(
        String providerId,
        String providerType,
        String modelName,
        String apiKeyRef,
        BudgetLimit budgetLimit,
        List<String> fallbackProviders) {

    public ModelSelection {
        fallbackProviders = fallbackProviders == null ? List.of() : List.copyOf(fallbackProviders);
    }

    public ModelProviderRequest providerRequest() {
        return new ModelProviderRequest(providerId, modelName, apiKeyRef);
    }
}
