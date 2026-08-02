package com.qaforge.infrastructure.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabMergeRequestResponse(
    long iid,
    String title,
    String description,
    List<String> labels,
    String target_branch,
    String source_branch,
    String sha,
    Author author,
    String web_url
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(String username) {}
}
