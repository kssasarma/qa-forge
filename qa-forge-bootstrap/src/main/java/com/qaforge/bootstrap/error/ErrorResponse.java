package com.qaforge.bootstrap.error;

import java.time.Instant;

public record ErrorResponse(String errorCode, String message, String timestamp) {
    public ErrorResponse(String errorCode, String message) {
        this(errorCode, message, Instant.now().toString());
    }
}
