package com.harnessagent.production;

public interface BudgetCounterStore {

    BudgetCounter increment(String key, long tokens);
}
