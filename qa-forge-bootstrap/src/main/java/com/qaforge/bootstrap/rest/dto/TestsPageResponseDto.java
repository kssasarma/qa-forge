package com.qaforge.bootstrap.rest.dto;

import java.util.List;

/** Response body for {@code GET /api/v1/tests} (PRD §12.5). */
public record TestsPageResponseDto(
    List<TestCaseResponseDto> tests,
    long totalElements,
    int totalPages,
    int page,
    int size
) {
    public static TestsPageResponseDto of(List<TestCaseResponseDto> pageContent, long totalElements, int page, int size) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new TestsPageResponseDto(pageContent, totalElements, totalPages, page, size);
    }
}
