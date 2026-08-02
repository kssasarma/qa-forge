package com.qaforge.bootstrap.rest;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config needed to turn a bare webhook event (which only identifies a PR/MR) into a full
 * {@code AnalysisRequest}/{@code RegressionRequest} — PRD §12.3/§12.4 specify the event
 * routing but not where the target app or output directory come from for a fully-automated
 * trigger, so this is a documented, pragmatic extension (see docs/IMPLEMENTATION_STATUS.md).
 */
@ConfigurationProperties(prefix = "qaforge.webhook")
public record WebhookProperties(
    String targetAppBaseUrl,
    String openApiSpecUrl,
    String outputBaseDirectory
) {
    public WebhookProperties {
        if (outputBaseDirectory == null || outputBaseDirectory.isBlank()) {
            outputBaseDirectory = "/tmp/qa-forge-tests";
        }
    }
}
