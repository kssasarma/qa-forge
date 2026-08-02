package com.qaforge.infrastructure.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One entry of {@code GET /projects/{id}/merge_requests/{iid}/diffs}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabDiffResponse(
    String old_path,
    String new_path,
    String diff,
    boolean new_file,
    boolean deleted_file,
    boolean renamed_file
) {}
