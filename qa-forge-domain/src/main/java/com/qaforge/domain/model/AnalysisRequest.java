package com.qaforge.domain.model;

/**
 * Input to {@code AnalyzePort}. {@code openApiSpecUrl} is optional: omitting it disables
 * RestAssured generation. {@code triggeredBy} is "cli" or "webhook".
 */
public record AnalysisRequest(
    String vcsType,
    String repositoryFullName,
    String prNumber,
    String targetAppBaseUrl,
    String testOutputDirectory,
    String openApiSpecUrl,
    String triggeredBy
) {}
