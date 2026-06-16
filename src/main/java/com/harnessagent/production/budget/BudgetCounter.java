package com.harnessagent.production.budget;

public record BudgetCounter(
        String key,
        long requests,
        long tokens) {
}
