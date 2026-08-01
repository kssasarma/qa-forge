package com.qaforge.infrastructure.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One entry of {@code GET /repos/{owner}/{repo}/pulls/{number}/files}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubFileResponse(
    String filename,
    String status,
    int additions,
    int deletions,
    String patch
) {}
