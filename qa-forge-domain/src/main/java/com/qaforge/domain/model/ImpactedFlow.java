package com.qaforge.domain.model;

/** {@code severity} is one of CRITICAL, HIGH, MEDIUM, LOW. */
public record ImpactedFlow(
    String flow,
    String reason,
    String severity
) {}
