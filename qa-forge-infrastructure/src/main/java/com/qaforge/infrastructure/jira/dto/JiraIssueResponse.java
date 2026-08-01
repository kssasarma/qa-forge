package com.qaforge.infrastructure.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraIssueResponse(String key, Fields fields) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fields(String summary, String description) {}
}
