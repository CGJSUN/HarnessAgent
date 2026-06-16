package com.harnessagent.production.infrastructure;

public class RetryableModelException extends RuntimeException {

    private final int statusCode;

    public RetryableModelException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
