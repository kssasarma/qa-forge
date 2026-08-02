package com.qaforge.infrastructure.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qaforge.github")
public record GitHubProperties(
    String baseUrl,
    String token,
    String webhookSecret
) {
    public GitHubProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.github.com";
        }
    }
}
