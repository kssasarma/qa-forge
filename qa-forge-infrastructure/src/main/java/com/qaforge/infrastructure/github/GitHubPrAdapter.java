package com.qaforge.infrastructure.github;

import com.qaforge.domain.exception.PrNotFoundException;
import com.qaforge.domain.model.ChangedFile;
import com.qaforge.domain.model.CodeDiff;
import com.qaforge.domain.model.PullRequest;
import com.qaforge.domain.port.out.PullRequestPort;
import com.qaforge.infrastructure.github.dto.GitHubCommentResponse;
import com.qaforge.infrastructure.github.dto.GitHubFileResponse;
import com.qaforge.infrastructure.github.dto.GitHubPullRequestResponse;
import com.qaforge.infrastructure.support.DiffTruncator;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Implements {@link PullRequestPort} against the GitHub REST API (PRD §12.10 G-10, §16.3). */
@Component
public class GitHubPrAdapter implements PullRequestPort {

    private final RestClient restClient;

    public GitHubPrAdapter(RestClient.Builder restClientBuilder, GitHubProperties properties) {
        this.restClient = restClientBuilder
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .build();
    }

    @Override
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public PullRequest fetch(String repositoryFullName, String prNumber) {
        String[] ownerRepo = splitRepository(repositoryFullName);
        GitHubPullRequestResponse pr = fetchRaw(ownerRepo[0], ownerRepo[1], prNumber);
        List<String> comments = fetchComments(ownerRepo[0], ownerRepo[1], prNumber);

        return new PullRequest(
            String.valueOf(pr.number()),
            "github",
            repositoryFullName,
            pr.title(),
            pr.body(),
            pr.labels() == null ? List.of() : pr.labels().stream().map(GitHubPullRequestResponse.Label::name).toList(),
            pr.base() == null ? null : pr.base().ref(),
            pr.head() == null ? null : pr.head().ref(),
            pr.head() == null ? null : pr.head().sha(),
            pr.user() == null ? null : pr.user().login(),
            comments,
            pr.html_url()
        );
    }

    @Override
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public CodeDiff fetchDiff(String repositoryFullName, String prNumber) {
        String[] ownerRepo = splitRepository(repositoryFullName);
        String rawDiff = fetchRawDiffText(ownerRepo[0], ownerRepo[1], prNumber);
        List<GitHubFileResponse> files = fetchFiles(ownerRepo[0], ownerRepo[1], prNumber);

        List<ChangedFile> changedFiles = files.stream()
            .map(f -> new ChangedFile(f.filename(), mapChangeType(f.status()), f.additions(), f.deletions(), f.patch()))
            .toList();

        DiffTruncator.Result truncated = DiffTruncator.truncate(rawDiff);
        return new CodeDiff(truncated.diff(), changedFiles, truncated.truncated());
    }

    private GitHubPullRequestResponse fetchRaw(String owner, String repo, String prNumber) {
        try {
            return restClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, prNumber)
                .retrieve()
                .body(GitHubPullRequestResponse.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new PrNotFoundException(owner + "/" + repo, prNumber);
            }
            throw e;
        }
    }

    private String fetchRawDiffText(String owner, String repo, String prNumber) {
        try {
            return restClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, prNumber)
                .accept(MediaType.parseMediaType("application/vnd.github.v3.diff"))
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new PrNotFoundException(owner + "/" + repo, prNumber);
            }
            throw e;
        }
    }

    private List<GitHubFileResponse> fetchFiles(String owner, String repo, String prNumber) {
        GitHubFileResponse[] files = restClient.get()
            .uri("/repos/{owner}/{repo}/pulls/{number}/files?per_page=100", owner, repo, prNumber)
            .retrieve()
            .body(GitHubFileResponse[].class);
        return files == null ? List.of() : List.of(files);
    }

    private List<String> fetchComments(String owner, String repo, String prNumber) {
        try {
            GitHubCommentResponse[] comments = restClient.get()
                .uri("/repos/{owner}/{repo}/issues/{number}/comments?per_page=100", owner, repo, prNumber)
                .retrieve()
                .body(GitHubCommentResponse[].class);
            return comments == null ? List.of() : List.of(comments).stream().map(GitHubCommentResponse::body).toList();
        } catch (RestClientException e) {
            return List.of();
        }
    }

    private String mapChangeType(String githubStatus) {
        return switch (githubStatus) {
            case "added" -> "ADDED";
            case "removed" -> "DELETED";
            case "renamed" -> "RENAMED";
            default -> "MODIFIED";
        };
    }

    private String[] splitRepository(String repositoryFullName) {
        String[] parts = repositoryFullName.split("/", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("repositoryFullName must be 'owner/repo', got: " + repositoryFullName);
        }
        return parts;
    }
}
