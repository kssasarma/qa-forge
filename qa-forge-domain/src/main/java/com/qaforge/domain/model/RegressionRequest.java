package com.qaforge.domain.model;

/** Input to {@code RegressionPort}. */
public record RegressionRequest(
    String vcsType,
    String repositoryFullName,
    String prNumber,
    String targetAppBaseUrl,
    String triggeredBy
) {}
