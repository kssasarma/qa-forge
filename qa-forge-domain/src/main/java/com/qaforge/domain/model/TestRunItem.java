package com.qaforge.domain.model;

/** One test's result within a {@link TestRun} (maps to the {@code test_run_items} table). */
public record TestRunItem(
    String testCaseId,
    String fileName,
    String scenarioId,
    TestLayer testLayer,
    ExecutionStatus status,
    String errorMessage,
    long durationMs,
    int retryCount,
    boolean selfHealed
) {}
