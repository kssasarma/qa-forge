package com.qaforge.bootstrap.rest.dto;

import com.qaforge.domain.model.TestRun;
import java.time.OffsetDateTime;
import java.util.List;

/** Response body for {@code GET /api/v1/runs/{runId}} (PRD §12.6). */
public record RunDetailResponseDto(
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
    OffsetDateTime createdAt,
    List<RunItemResponseDto> items
) {
    public static RunDetailResponseDto from(TestRun run) {
        return new RunDetailResponseDto(
            run.id(), run.repository(), run.prNumber(), run.runType(), run.triggeredBy(),
            run.outcome(), run.gateResult(), run.totalCount(), run.passedCount(), run.failedCount(),
            run.passRate() == null ? 0.0 : run.passRate(), run.durationMs(), run.createdAt(),
            run.items().stream().map(RunItemResponseDto::from).toList());
    }
}
