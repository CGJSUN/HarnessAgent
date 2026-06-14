package com.harnessagent.tooling;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ToolParameterSchema(
        Set<String> requiredParameters,
        Set<String> optionalParameters,
        Map<String, Set<String>> allowedValues,
        Set<String> sensitiveParameters) {

    public ToolParameterSchema {
        requiredParameters = safeSet(requiredParameters);
        optionalParameters = safeSet(optionalParameters);
        allowedValues = safeValueMap(allowedValues);
        sensitiveParameters = safeSet(sensitiveParameters);
    }

    public static ToolParameterSchema empty() {
        return new ToolParameterSchema(Set.of(), Set.of(), Map.of(), Set.of());
    }

    public Optional<String> validate(Map<String, Object> parameters) {
        Map<String, Object> safeParameters = parameters == null ? Map.of() : parameters;
        Set<String> knownParameters = allowedParameters();
        for (String key : safeParameters.keySet()) {
            if (!knownParameters.contains(key)) {
                return Optional.of("Unsupported parameter: " + key);
            }
        }
        for (String required : requiredParameters) {
            if (!safeParameters.containsKey(required) || safeParameters.get(required) == null) {
                return Optional.of("Missing required parameter: " + required);
            }
        }
        for (Map.Entry<String, Set<String>> entry : allowedValues.entrySet()) {
            Object value = safeParameters.get(entry.getKey());
            if (value != null && !entry.getValue().contains(String.valueOf(value))) {
                return Optional.of("Parameter is not whitelisted: " + entry.getKey());
            }
        }
        return Optional.empty();
    }

    public Set<String> allowedParameters() {
        return Stream.concat(requiredParameters.stream(), optionalParameters.stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> safeSet(Set<String> input) {
        if (input == null) {
            return Set.of();
        }
        return input.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Map<String, Set<String>> safeValueMap(Map<String, Set<String>> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> result = new LinkedHashMap<>();
        input.forEach((key, values) -> {
            if (key != null && !key.isBlank()) {
                result.put(key.trim(), safeSet(values));
            }
        });
        return Map.copyOf(result);
    }
}
