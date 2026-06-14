package com.harnessagent.security;

public record ProtectedResource(
        String id,
        String title,
        ResourceAccessPolicy policy) {
}
