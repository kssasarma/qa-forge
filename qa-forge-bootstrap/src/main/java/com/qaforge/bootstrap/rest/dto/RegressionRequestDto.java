package com.qaforge.bootstrap.rest.dto;

import com.qaforge.domain.model.RegressionRequest;
import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/regression} (PRD §12.2). */
public record RegressionRequestDto(
    String vcsType,
    @NotBlank(message = "repositoryFullName is required") String repositoryFullName,
    @NotBlank(message = "prNumber is required") String prNumber,
    @NotBlank(message = "targetAppBaseUrl is required") String targetAppBaseUrl
) {
    public RegressionRequest toDomain(String triggeredBy) {
        return new RegressionRequest(
            vcsType == null || vcsType.isBlank() ? "github" : vcsType,
            repositoryFullName, prNumber, targetAppBaseUrl, triggeredBy);
    }
}
