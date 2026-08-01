package com.qaforge.bootstrap.rest.dto;

import com.qaforge.domain.model.TestRun;
import java.time.OffsetDateTime;

/** One entry of {@code GET /api/v1/runs} (PRD §12.7). */
public record RunSummaryResponseDto(
    String runId,
    String repository,
    String prNumber,
    String runType,
    String triggeredBy,
    String outcome,
    String gateResult,
    int total,
    int passed,
    int failed,
    double passRatePercent,
    Long durationMs,
    OffsetDateTime createdAt
) {
    public static RunSummaryResponseDto from(TestRun run) {
        return new RunSummaryResponseDto(
            run.id(), run.repository(), run.prNumber(), run.runType(), run.triggeredBy(),
            run.outcome(), run.gateResult(), run.totalCount(), run.passedCount(), run.failedCount(),
            run.passRate() == null ? 0.0 : run.passRate(), run.durationMs(), run.createdAt());
    }
}
