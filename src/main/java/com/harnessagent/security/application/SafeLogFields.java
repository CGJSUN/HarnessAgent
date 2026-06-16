package com.harnessagent.security.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class SafeLogFields {

    private static final HexFormat HEX = HexFormat.of();
    private static final String EMPTY = "empty";
    private static final String HASH_PREFIX = "sha256:";
    private static final int HASH_LENGTH = 16;

    private SafeLogFields() {
    }

    public static String reasonCode(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unspecified";
        }
        String normalized = reason.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "unspecified" : normalized;
    }

    public static String digest(String value) {
        if (value == null || value.isBlank()) {
            return EMPTY;
        }
        byte[] hash = sha256(value.trim());
        return HASH_PREFIX + HEX.formatHex(hash, 0, HASH_LENGTH / 2);
    }

    public static String user(String userId) {
        return digest(userId);
    }

    public static String session(String sessionId) {
        return digest(sessionId);
    }

    public static String idempotency(String idempotencyKey) {
        return digest(idempotencyKey);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }
}
