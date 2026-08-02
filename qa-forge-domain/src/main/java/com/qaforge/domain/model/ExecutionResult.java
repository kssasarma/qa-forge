package com.qaforge.domain.model;

public record ExecutionResult(
    String scenarioId,
    String fileName,
    ExecutionStatus status,
    String errorMessage,
    long durationMs,
    int retryCount
) {}
