package com.qaforge.infrastructure.jira;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qaforge.jira")
public record JiraProperties(
    boolean enabled,
    String baseUrl,
    String token,
    String projectKey
) {}
