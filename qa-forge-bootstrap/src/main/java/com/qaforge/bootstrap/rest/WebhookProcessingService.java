package com.qaforge.bootstrap.rest;

import com.qaforge.domain.model.AnalysisRequest;
import com.qaforge.domain.model.RegressionRequest;
import com.qaforge.domain.port.in.AnalyzePort;
import com.qaforge.domain.port.in.RegressionPort;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Background processing for webhook-triggered analyze/regression runs (PRD §12.3/§12.4,
 * §5.3 — webhooks return 202 immediately and process on {@code webhookExecutor}).
 *
 * <p>{@code @Async} is on these public methods (not on a private helper the controller calls
 * directly) so Spring's proxy actually intercepts the call. Failures are logged here only —
 * {@code AnalyzePort}/{@code RegressionPort} already post a failure check back to the PR/MR
 * themselves (PRD §16.4), so there's nothing else to report to the caller of a 202 response.
 */
@Service
public class WebhookProcessingService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);

    private final AnalyzePort analyzePort;
    private final RegressionPort regressionPort;
    private final WebhookProperties webhookProperties;

    public WebhookProcessingService(AnalyzePort analyzePort, RegressionPort regressionPort,
                                     WebhookProperties webhookProperties) {
        this.analyzePort = analyzePort;
        this.regressionPort = regressionPort;
        this.webhookProperties = webhookProperties;
    }

    @Async("webhookExecutor")
    public void analyzeAsync(String vcsType, String repositoryFullName, String prNumber, String deliveryId) {
        try {
            analyzePort.analyze(buildAnalysisRequest(vcsType, repositoryFullName, prNumber));
        } catch (RuntimeException e) {
            log.error("Webhook-triggered analyze failed for {}#{} (delivery {})", repositoryFullName, prNumber, deliveryId, e);
        }
    }

    @Async("webhookExecutor")
    public void regressionThenAnalyzeAsync(String vcsType, String repositoryFullName, String prNumber, String deliveryId) {
        try {
            regressionPort.runRegression(new RegressionRequest(
                vcsType, repositoryFullName, prNumber, webhookProperties.targetAppBaseUrl(), "webhook"));
            analyzePort.analyze(buildAnalysisRequest(vcsType, repositoryFullName, prNumber));
        } catch (RuntimeException e) {
            log.error("Webhook-triggered regression+analyze failed for {}#{} (delivery {})",
                repositoryFullName, prNumber, deliveryId, e);
        }
    }

    @Async("webhookExecutor")
    public void regressionAsync(String vcsType, String repositoryFullName, String prNumber, String deliveryId) {
        try {
            regressionPort.runRegression(new RegressionRequest(
                vcsType, repositoryFullName, prNumber, webhookProperties.targetAppBaseUrl(), "webhook"));
        } catch (RuntimeException e) {
            log.error("Webhook-triggered regression failed for {}#{} (delivery {})", repositoryFullName, prNumber, deliveryId, e);
        }
    }

    private AnalysisRequest buildAnalysisRequest(String vcsType, String repositoryFullName, String prNumber) {
        String outputDirectory = Path.of(webhookProperties.outputBaseDirectory(),
            repositoryFullName.replace('/', '-')).toString();
        return new AnalysisRequest(
            vcsType, repositoryFullName, prNumber, webhookProperties.targetAppBaseUrl(),
            outputDirectory, webhookProperties.openApiSpecUrl(), "webhook");
    }
}
