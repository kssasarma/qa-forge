package com.qaforge.domain.exception;

/** Root of the QA Forge exception hierarchy. Maps to HTTP 502 UPSTREAM_ERROR by default. */
public class QaForgeException extends RuntimeException {

    public QaForgeException(String message) {
        super(message);
    }

    public QaForgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
