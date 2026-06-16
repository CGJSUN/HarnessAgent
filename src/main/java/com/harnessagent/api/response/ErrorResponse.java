package com.harnessagent.api.response;

import java.time.Instant;

public record ErrorResponse(String message, Instant timestamp) {

    public static ErrorResponse of(String message) {
        return new ErrorResponse(message, Instant.now());
    }
}
