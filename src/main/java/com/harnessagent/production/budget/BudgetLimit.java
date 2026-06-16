package com.harnessagent.production.budget;

public record BudgetLimit(
        long requestLimit,
        long tokenLimit) {
}
