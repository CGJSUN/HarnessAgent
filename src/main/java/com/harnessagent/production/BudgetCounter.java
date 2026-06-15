package com.harnessagent.production;

public record BudgetCounter(
        String key,
        long requests,
        long tokens) {
}
