package com.harnessagent.security;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentSecretStore implements SecretStore {

    @Override
    public Optional<String> resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        String trimmed = reference.trim();
        if (trimmed.startsWith("env:")) {
            return Optional.ofNullable(System.getenv(trimmed.substring("env:".length())));
        }
        if (trimmed.startsWith("literal:")) {
            return Optional.of(trimmed.substring("literal:".length()));
        }
        return Optional.empty();
    }
}
