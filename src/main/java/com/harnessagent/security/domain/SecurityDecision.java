package com.harnessagent.security.domain;

public record SecurityDecision(boolean allowed, String reason) {

    public static SecurityDecision allow() {
        return new SecurityDecision(true, "");
    }

    public static SecurityDecision deny(String reason) {
        return new SecurityDecision(false, reason == null ? "denied" : reason);
    }
}
