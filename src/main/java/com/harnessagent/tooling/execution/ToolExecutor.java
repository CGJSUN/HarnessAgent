package com.harnessagent.tooling.execution;

import java.util.Map;
import com.harnessagent.tooling.domain.ToolDefinition;

public interface ToolExecutor {

    boolean supports(ToolDefinition definition);

    Map<String, Object> execute(ToolDefinition definition, Map<String, Object> parameters);
}
