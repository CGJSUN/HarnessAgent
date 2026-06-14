package com.harnessagent.production;

public record StateStorePlan(
        StateStoreType type,
        String location,
        boolean distributed) {
}
