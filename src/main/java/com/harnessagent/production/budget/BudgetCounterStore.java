package com.harnessagent.production.budget;

public interface BudgetCounterStore {

    BudgetCounter increment(String key, long tokens);
}
