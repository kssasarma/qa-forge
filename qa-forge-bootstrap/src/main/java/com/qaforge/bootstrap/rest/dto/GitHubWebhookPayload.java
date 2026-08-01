package com.qaforge.bootstrap.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubWebhookPayload(String action, Repository repository, PullRequest pull_request) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(String full_name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequest(long number) {}
}
