package com.harnessagent.tooling;

import java.util.Map;

public interface ToolExecutor {

    boolean supports(ToolDefinition definition);

    Map<String, Object> execute(ToolDefinition definition, Map<String, Object> parameters);
}
