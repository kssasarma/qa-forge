package com.qaforge.infrastructure.gitlab;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qaforge.gitlab")
public record GitLabProperties(
    String baseUrl,
    String token,
    String webhookToken
) {
    public GitLabProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://gitlab.com";
        }
    }
}
