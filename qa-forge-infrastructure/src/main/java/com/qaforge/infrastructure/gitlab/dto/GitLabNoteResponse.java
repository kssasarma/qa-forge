package com.qaforge.infrastructure.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabNoteResponse(String body, boolean system) {}
