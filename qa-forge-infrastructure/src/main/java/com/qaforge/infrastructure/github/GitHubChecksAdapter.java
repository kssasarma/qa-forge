package com.qaforge.infrastructure.github;

import com.qaforge.domain.model.PullRequest;
import com.qaforge.domain.port.out.VcsChecksPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * GitHub Check Runs API implementation of the "checks" behavior (PRD §12.10 G-13). Does not
 * implement {@link VcsChecksPort} directly — {@code VcsChecksRouter} is the single
 * {@link VcsChecksPort} bean and dispatches to this or {@code GitLabChecksAdapter} by
 * {@code PullRequest.vcsType()}, since a plain constructor-injected {@code VcsChecksPort}
 * can't disambiguate between two implementations at wiring time.
 *
 * <p>{@code @Retryable} is applied directly to these methods (not to a shared private helper)
 * because Spring's proxy-based AOP only intercepts calls that arrive through the bean's proxy
 * — a {@code this.someHelper()} call from within the class would silently bypass retry
 * entirely.
 */
@Component
public class GitHubChecksAdapter {

    private static final Logger log = LoggerFactory.getLogger(GitHubChecksAdapter.class);

    private final RestClient restClient;

    public GitHubChecksAdapter(RestClient.Builder restClientBuilder, GitHubProperties properties) {
        this.restClient = restClientBuilder
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .build();
    }

    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public void postPending(PullRequest pr, String checkName, String detailsUrl) {
        postCheckRun(pr, checkName, "in_progress", null, "Running", "QA Forge analysis is in progress.", detailsUrl);
    }

    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public void postSuccess(PullRequest pr, String checkName, String summary) {
        postCheckRun(pr, checkName, "completed", "success", "Passed", summary, null);
    }

    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public void postFailure(PullRequest pr, String checkName, String summary) {
        postCheckRun(pr, checkName, "completed", "failure", "Failed", summary, null);
    }

    @Recover
    public void recoverFromPostFailure(RestClientException e, PullRequest pr, String checkName, String thirdArg) {
        log.error("Giving up posting GitHub check run '{}' for {}#{} after retries", checkName,
            pr.repositoryFullName(), pr.id(), e);
    }

    private void postCheckRun(PullRequest pr, String checkName, String status, String conclusion,
                               String title, String summary, String detailsUrl) {
        if (pr.headSha() == null) {
            log.warn("Skipping GitHub check run '{}': no head SHA for PR {}", checkName, pr.id());
            return;
        }
        String[] ownerRepo = pr.repositoryFullName().split("/", 2);
        CheckRunRequest body = new CheckRunRequest(
            checkName, pr.headSha(), status, conclusion, detailsUrl,
            new CheckRunRequest.Output(title, summary));

        restClient.post()
            .uri("/repos/{owner}/{repo}/check-runs", ownerRepo[0], ownerRepo[1])
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    private record CheckRunRequest(String name, String head_sha, String status, String conclusion,
                                    String details_url, Output output) {
        private record Output(String title, String summary) {}
    }
}
