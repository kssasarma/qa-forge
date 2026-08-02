package com.qaforge.domain.model;

/**
 * Output of {@code AnalyzePort.analyze}, per PRD §12.1. {@code outcome} is SUCCESS,
 * PARTIAL_FAILURE, or ERROR.
 */
public record AnalysisResult(
    String runId,
    String prNumber,
    String repository,
    String outcome,
    int newTestsGenerated,
    int updatedTests,
    int skippedScenarios,
    int markedObsolete,
    ExecutionSummary executionSummary,
    GeneratedFilesSummary generatedFiles,
    long durationMs
) {
    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_PARTIAL_FAILURE = "PARTIAL_FAILURE";
    public static final String OUTCOME_ERROR = "ERROR";
}
