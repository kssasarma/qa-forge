package com.qaforge.domain.exception;

/** Thrown when the Playwright MCP execution pipeline fails unrecoverably. */
public class TestExecutionException extends QaForgeException {

    public TestExecutionException(String message) {
        super(message);
    }

    public TestExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
