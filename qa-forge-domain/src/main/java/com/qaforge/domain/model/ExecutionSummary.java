package com.qaforge.domain.model;

public record ExecutionSummary(
    int total,
    int passed,
    int failed,
    int errors,
    int selfHealed,
    double passRatePercent
) {}
