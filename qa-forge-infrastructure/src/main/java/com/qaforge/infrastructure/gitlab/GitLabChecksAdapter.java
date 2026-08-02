package com.qaforge.infrastructure.gitlab;

import com.qaforge.domain.model.PullRequest;
import com.qaforge.domain.port.out.VcsChecksPort;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * GitLab commit statuses API implementation of the "checks" behavior (PRD §12.10 G-13). Does
 * not implement {@link VcsChecksPort} directly — see {@code GitHubChecksAdapter}'s javadoc for
 * why {@code VcsChecksRouter} is the single {@link VcsChecksPort} bean instead.
 */
@Component
public class GitLabChecksAdapter {

    private static final Logger log = LoggerFactory.getLogger(GitLabChecksAdapter.class);

    private final RestClient restClient;

    public GitLabChecksAdapter(RestClient.Builder restClientBuilder, GitLabProperties properties) {
        this.restClient = restClientBuilder
            .baseUrl(properties.baseUrl() + "/api/v4")
            .defaultHeader("PRIVATE-TOKEN", properties.token())
            .build();
    }

    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public void postPending(PullRequest pr, String checkName, String detailsUrl) {
        postStatus(pr, checkName, "pending", "QA Forge analysis is in progress.", detailsUrl);
    }

    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public void postSuccess(PullRequest pr, String checkName, String summary) {
        postStatus(pr, checkName, "success", summary, null);
    }

    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public void postFailure(PullRequest pr, String checkName, String summary) {
        postStatus(pr, checkName, "failed", summary, null);
    }

    @Recover
    public void recoverFromPostFailure(RestClientException e, PullRequest pr, String checkName, String thirdArg) {
        log.error("Giving up posting GitLab commit status '{}' for {}#{} after retries", checkName,
            pr.repositoryFullName(), pr.id(), e);
    }

    private void postStatus(PullRequest pr, String checkName, String state, String description, String targetUrl) {
        if (pr.headSha() == null) {
            log.warn("Skipping GitLab commit status '{}': no head SHA for MR {}", checkName, pr.id());
            return;
        }
        String encodedProject = URLEncoder.encode(pr.repositoryFullName(), StandardCharsets.UTF_8);

        restClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/projects/{id}/statuses/{sha}")
                .queryParam("state", state)
                .queryParam("name", checkName)
                .queryParam("description", description)
                .queryParamIfPresent("target_url", java.util.Optional.ofNullable(targetUrl))
                .build(encodedProject, pr.headSha()))
            .retrieve()
            .toBodilessEntity();
    }
}
