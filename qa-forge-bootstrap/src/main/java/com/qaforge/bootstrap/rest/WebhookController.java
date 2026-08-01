package com.qaforge.bootstrap.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaforge.bootstrap.rest.dto.GitHubWebhookPayload;
import com.qaforge.bootstrap.rest.dto.GitLabWebhookPayload;
import com.qaforge.infrastructure.github.GitHubProperties;
import com.qaforge.infrastructure.gitlab.GitLabProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code POST /api/v1/webhook/github} and {@code POST /api/v1/webhook/gitlab} (PRD §12.3/§12.4,
 * §17.2). Validates the signature/token, then returns 202 immediately and processes on
 * {@code webhookExecutor} via {@link WebhookProcessingService}.
 */
@RestController
@RequestMapping("/api/v1/webhook")
public class WebhookController {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final WebhookProcessingService webhookProcessingService;
    private final GitHubProperties gitHubProperties;
    private final GitLabProperties gitLabProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebhookController(WebhookProcessingService webhookProcessingService,
                              GitHubProperties gitHubProperties, GitLabProperties gitLabProperties) {
        this.webhookProcessingService = webhookProcessingService;
        this.gitHubProperties = gitHubProperties;
        this.gitLabProperties = gitLabProperties;
    }

    @PostMapping("/github")
    public ResponseEntity<Void> github(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestBody byte[] body) {

        validateGithubSignature(signature, body);

        if (!"pull_request".equals(event)) {
            return ResponseEntity.noContent().build();
        }

        GitHubWebhookPayload payload = parse(body, GitHubWebhookPayload.class);
        String repositoryFullName = payload.repository().full_name();
        String prNumber = String.valueOf(payload.pull_request().number());

        switch (payload.action()) {
            case "opened", "synchronize" -> webhookProcessingService.analyzeAsync("github", repositoryFullName, prNumber, deliveryId);
            case "ready_for_review" -> webhookProcessingService.regressionThenAnalyzeAsync("github", repositoryFullName, prNumber, deliveryId);
            default -> {
                return ResponseEntity.noContent().build();
            }
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/gitlab")
    public ResponseEntity<Void> gitlab(
            @RequestHeader("X-Gitlab-Token") String token,
            @RequestBody byte[] body) {

        validateGitlabToken(token);

        GitLabWebhookPayload payload = parse(body, GitLabWebhookPayload.class);
        if (!"merge_request".equals(payload.object_kind()) || payload.object_attributes() == null) {
            return ResponseEntity.noContent().build();
        }

        String projectPath = payload.project().path_with_namespace();
        String mrIid = String.valueOf(payload.object_attributes().iid());

        switch (payload.object_attributes().action()) {
            case "open", "update" -> webhookProcessingService.analyzeAsync("gitlab", projectPath, mrIid, null);
            case "approved" -> webhookProcessingService.regressionAsync("gitlab", projectPath, mrIid, null);
            default -> {
                return ResponseEntity.noContent().build();
            }
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    private void validateGithubSignature(String signature, byte[] body) {
        if (signature == null || !signature.startsWith("sha256=")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing signature");
        }
        String expected = "sha256=" + hmacSha256(gitHubProperties.webhookSecret(), body);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
        }
    }

    private void validateGitlabToken(String token) {
        String expected = gitLabProperties.webhookToken();
        if (expected == null || token == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }

    private String hmacSha256(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] rawHmac = mac.doFinal(body);
            StringBuilder hex = new StringBuilder(rawHmac.length * 2);
            for (byte b : rawHmac) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to compute HMAC", e);
        }
    }

    private <T> T parse(byte[] body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook payload", e);
        }
    }
}
