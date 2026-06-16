package com.harnessagent.security.domain;

public record ProtectedResource(
        String id,
        String title,
        ResourceAccessPolicy policy) {
}
