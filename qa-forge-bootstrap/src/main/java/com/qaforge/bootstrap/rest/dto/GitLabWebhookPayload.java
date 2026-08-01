package com.qaforge.bootstrap.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabWebhookPayload(String object_kind, ObjectAttributes object_attributes, Project project) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObjectAttributes(String action, long iid) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Project(String path_with_namespace) {}
}
