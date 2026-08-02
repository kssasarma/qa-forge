package com.qaforge.infrastructure.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestResponse(
    long number,
    String title,
    String body,
    List<Label> labels,
    Ref base,
    Ref head,
    User user,
    String html_url
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Label(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ref(String ref, String sha) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(String login) {}
}
