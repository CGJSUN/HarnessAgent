package com.harnessagent.api.request;

import java.util.List;

public record PersonalPlanRequest(
        String goal,
        List<String> steps) {
}
