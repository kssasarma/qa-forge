package com.qaforge.bootstrap.rest.dto;

import com.qaforge.domain.model.TestRunItem;

/** One item within {@code GET /api/v1/runs/{runId}} (PRD §12.6). */
public record RunItemResponseDto(
    String fileName,
    String layer,
    String status,
    Long durationMs,
    int retryCount,
    boolean selfHealed,
    String errorMessage
) {
    public static RunItemResponseDto from(TestRunItem item) {
        return new RunItemResponseDto(
            item.fileName(), item.testLayer() == null ? null : item.testLayer().name(),
            item.status().name(), item.durationMs(), item.retryCount(), item.selfHealed(), item.errorMessage());
    }
}
