package com.qaforge.bootstrap.rest.dto;

import com.qaforge.domain.model.AnalysisRequest;
import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/analyze} (PRD §12.1). */
public record AnalyzeRequestDto(
    String vcsType,
    @NotBlank(message = "repositoryFullName is required") String repositoryFullName,
    @NotBlank(message = "prNumber is required") String prNumber,
    @NotBlank(message = "targetAppBaseUrl is required") String targetAppBaseUrl,
    @NotBlank(message = "testOutputDirectory is required") String testOutputDirectory,
    String openApiSpecUrl
) {
    public AnalysisRequest toDomain(String triggeredBy) {
        return new AnalysisRequest(
            vcsType == null || vcsType.isBlank() ? "github" : vcsType,
            repositoryFullName, prNumber, targetAppBaseUrl, testOutputDirectory, openApiSpecUrl, triggeredBy);
    }
}
