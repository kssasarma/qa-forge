package com.qaforge.domain.model;

/** Output of {@code RegressionPort.runRegression}, per PRD §12.2. */
public record RegressionResult(
    String runId,
    String outcome,
    int total,
    int passed,
    int failed,
    int selfHealed,
    double passRatePercent,
    long durationMs,
    String gateResult,
    String blockedReason
) {}
