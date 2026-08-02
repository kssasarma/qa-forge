package com.qaforge.domain.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A persisted, registry-tracked test case (maps to the {@code test_cases} table).
 * {@code status} is ACTIVE or OBSOLETE.
 */
public record TestCase(
    String id,
    String fileName,
    String scenarioId,
    String scenarioTitle,
    ScenarioType scenarioType,
    TestLayer testLayer,
    String userFlow,
    String targetPath,
    boolean requiresAuth,
    List<String> tags,
    String prNumber,
    String repository,
    String vcsType,
    String headSha,
    String status,
    ExecutionStatus lastExecutionStatus,
    Long lastExecutionMs,
    int executionCount,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_OBSOLETE = "OBSOLETE";
}
