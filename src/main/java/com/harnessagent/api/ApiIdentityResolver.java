package com.harnessagent.api;

import com.harnessagent.security.domain.IdentityProviderType;
import com.harnessagent.security.domain.OwnerIdentity;
import com.harnessagent.security.domain.OwnerPrincipal;
import com.harnessagent.runtime.PersonalRuntimeDefaults;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ApiIdentityResolver {

    public OwnerPrincipal resolve(Map<String, String> headers, String requestOwnerId) {
        return new OwnerPrincipal(resolveOwner(headers, requestOwnerId));
    }

    public OwnerPrincipal resolveTrusted(Map<String, String> headers, String requestOwnerId) {
        return new OwnerPrincipal(resolveTrustedOwner(headers, requestOwnerId));
    }

    public OwnerIdentity resolveOwner(Map<String, String> headers, String requestOwnerId) {
        String headerOwnerId = header(headers, "X-Owner-Id");
        if (headerOwnerId == null) {
            return OwnerIdentity.local(PersonalRuntimeDefaults.ownerId(requestOwnerId));
        }
        requireMatch("ownerId", requestOwnerId, headerOwnerId);
        return OwnerIdentity.trusted(headerOwnerId.trim(), provider(header(headers, "X-Identity-Provider")));
    }

    public OwnerIdentity resolveTrustedOwner(Map<String, String> headers, String requestOwnerId) {
        String headerOwnerId = header(headers, "X-Owner-Id");
        requireMatch("ownerId", requestOwnerId, headerOwnerId);
        return OwnerIdentity.trusted(headerOwnerId.trim(), provider(header(headers, "X-Identity-Provider")));
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

}
