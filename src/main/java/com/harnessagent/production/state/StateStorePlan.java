package com.harnessagent.production.state;

public record StateStorePlan(
        StateStoreType type,
        String location,
        boolean distributed,
        boolean productionDurable) {

    public static StateStorePlan local(String location) {
        return new StateStorePlan(StateStoreType.LOCAL_JSON, location, false, false);
    }

    public static StateStorePlan redis(String location) {
        return new StateStorePlan(StateStoreType.REDIS, location, true, true);
    }

    public static StateStorePlan mysql(String location) {
        return new StateStorePlan(StateStoreType.MYSQL, location, true, true);
    }
}
