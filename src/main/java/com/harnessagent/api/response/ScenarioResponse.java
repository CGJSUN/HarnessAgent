package com.harnessagent.api.response;

import java.util.List;

public record ScenarioResponse(String scenario, List<String> acceptanceCriteria) {
}
