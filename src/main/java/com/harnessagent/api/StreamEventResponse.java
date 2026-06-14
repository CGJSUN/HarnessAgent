package com.harnessagent.api;

public record StreamEventResponse(String type, String content, boolean terminal) {
}
