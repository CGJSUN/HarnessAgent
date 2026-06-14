package com.harnessagent.security;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PromptInjectionGuard {

    private static final List<String> BLOCKED_PATTERNS = List.of(
            "ignore previous",
            "ignore all previous",
            "bypass policy",
            "disable policy",
            "reveal secret",
            "show api key",
            "tool token",
            "system prompt",
            "忽略以上",
            "忽略之前",
            "绕过",
            "泄露",
            "提权",
            "系统提示词");

    public SecurityDecision inspectText(String text) {
        if (text == null || text.isBlank()) {
            return SecurityDecision.allow();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        boolean blocked = BLOCKED_PATTERNS.stream().anyMatch(normalized::contains);
        return blocked
                ? SecurityDecision.deny("Potential prompt injection or unsafe instruction detected")
                : SecurityDecision.allow();
    }

    public SecurityDecision inspectToolParameters(Map<String, Object> parameters, Set<String> allowedKeys) {
        if (parameters == null || parameters.isEmpty()) {
            return SecurityDecision.allow();
        }
        for (String key : parameters.keySet()) {
            if (allowedKeys != null && !allowedKeys.isEmpty() && !allowedKeys.contains(key)) {
                return SecurityDecision.deny("Tool parameter is not whitelisted: " + key);
            }
            SecurityDecision textDecision = inspectText(String.valueOf(parameters.get(key)));
            if (!textDecision.allowed()) {
                return textDecision;
            }
        }
        return SecurityDecision.allow();
    }
}
