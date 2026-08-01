package com.qaforge.bootstrap.rest.dto;

import com.qaforge.domain.model.TestCase;
import java.time.OffsetDateTime;
import java.util.List;

/** One entry of {@code GET /api/v1/tests} (PRD §12.5). */
public record TestCaseResponseDto(
    String id,
    String fileName,
    String scenarioTitle,
    String layer,
    String userFlow,
    String prNumber,
    String status,
    List<String> tags,
    String lastExecutionStatus,
    Long lastExecutionMs,
    int executionCount,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static TestCaseResponseDto from(TestCase testCase) {
        return new TestCaseResponseDto(
            testCase.id(), testCase.fileName(), testCase.scenarioTitle(), testCase.testLayer().name(),
            testCase.userFlow(), testCase.prNumber(), testCase.status(), testCase.tags(),
            testCase.lastExecutionStatus() == null ? null : testCase.lastExecutionStatus().name(),
            testCase.lastExecutionMs(), testCase.executionCount(), testCase.createdAt(), testCase.updatedAt());
    }
}
