package com.harnessagent.api;

import com.harnessagent.security.domain.IdentityProviderType;
import com.harnessagent.security.domain.SecurityPrincipal;
import com.harnessagent.runtime.PersonalRuntimeDefaults;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ApiIdentityResolver {

    public SecurityPrincipal resolve(
            Map<String, String> headers,
            String requestTenantId,
            String requestUserId,
            Set<String> requestRoles,
            Set<String> requestDepartments) {
        String headerTenantId = header(headers, "X-Tenant-Id");
        String headerUserId = header(headers, "X-User-Id");
        if (headerTenantId == null && headerUserId == null) {
            return new SecurityPrincipal(
                    PersonalRuntimeDefaults.tenantId(requestTenantId),
                    PersonalRuntimeDefaults.ownerId(requestTenantId, requestUserId),
                    IdentityProviderType.INTERNAL,
                    safeSet(requestRoles),
                    safeSet(requestDepartments));
        }
        requireMatch("tenantId", requestTenantId, headerTenantId);
        requireMatch("userId", requestUserId, headerUserId);
        return new SecurityPrincipal(
                headerTenantId,
                headerUserId,
                provider(header(headers, "X-Identity-Provider")),
                csv(header(headers, "X-Roles")),
                csv(header(headers, "X-Departments")));
    }

    public SecurityPrincipal resolveTrusted(
            Map<String, String> headers,
            String requestTenantId,
            String requestUserId) {
        String headerTenantId = header(headers, "X-Tenant-Id");
        String headerUserId = header(headers, "X-User-Id");
        requireMatch("tenantId", requestTenantId, headerTenantId);
        requireMatch("userId", requestUserId, headerUserId);
        return new SecurityPrincipal(
                headerTenantId.trim(),
                headerUserId.trim(),
                provider(header(headers, "X-Identity-Provider")),
                csv(header(headers, "X-Roles")),
                csv(header(headers, "X-Departments")));
    }

    public String resolveTrustedAgentId(Map<String, String> headers, String requestAgentId) {
        String headerAgentId = header(headers, "X-Agent-Id");
        requireMatch("agentId", requestAgentId, headerAgentId);
        return headerAgentId.trim();
    }

    private static void requireMatch(String field, String requestValue, String trustedValue) {
        if (trustedValue == null || trustedValue.isBlank()) {
            throw new IllegalStateException("Authenticated " + field + " is required");
        }
        if (requestValue != null && !requestValue.isBlank() && !requestValue.trim().equals(trustedValue.trim())) {
            throw new IllegalStateException("Request " + field + " does not match authenticated identity");
        }
    }

    private static IdentityProviderType provider(String value) {
        if (value == null || value.isBlank()) {
            return IdentityProviderType.INTERNAL;
        }
        return IdentityProviderType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static Set<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> safeSet(Set<String> input) {
        return input == null ? Set.of() : input;
    }
}
