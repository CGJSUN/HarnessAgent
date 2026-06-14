package com.harnessagent.tooling;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DefaultToolExecutor implements ToolExecutor {

    @Override
    public boolean supports(ToolDefinition definition) {
        return true;
    }

    @Override
    public Map<String, Object> execute(ToolDefinition definition, Map<String, Object> parameters) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("tool", definition.name());
        output.put("ownerSystem", definition.ownerSystem());
        output.put("sourceType", definition.sourceType().name());
        output.put("sourceRef", definition.sourceRef());
        output.put("parameters", parameters);
        output.put("executedAt", Instant.now().toString());
        return output;
    }
}
