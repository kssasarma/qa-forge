package com.qaforge.domain.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A persisted regression or incremental run (maps to {@code test_runs} + {@code test_run_items}).
 * {@code runType} is REGRESSION or INCREMENTAL; {@code outcome} is PASSED, FAILED,
 * PARTIAL_FAILURE, or ERROR; {@code gateResult} is OPEN or BLOCKED.
 */
public record TestRun(
    String id,
    String repository,
    String vcsType,
    String prNumber,
    String runType,
    String triggeredBy,
    int totalCount,
    int passedCount,
    int failedCount,
    int errorCount,
    int skippedCount,
    Double passRate,
    Long durationMs,
    String outcome,
    String gateResult,
    OffsetDateTime createdAt,
    List<TestRunItem> items
) {
    public static final String RUN_TYPE_REGRESSION = "REGRESSION";
    public static final String RUN_TYPE_INCREMENTAL = "INCREMENTAL";

    public static final String GATE_OPEN = "OPEN";
    public static final String GATE_BLOCKED = "BLOCKED";
}
