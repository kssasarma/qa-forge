package com.qaforge.bootstrap.rest.dto;

import java.util.List;

/** Response body for {@code GET /api/v1/runs} (PRD §12.7). */
public record RunsPageResponseDto(
    List<RunSummaryResponseDto> runs,
    long totalElements,
    int totalPages,
    int page,
    int size
) {
    public static RunsPageResponseDto of(List<RunSummaryResponseDto> pageContent, long totalElements, int page, int size) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new RunsPageResponseDto(pageContent, totalElements, totalPages, page, size);
    }
}
