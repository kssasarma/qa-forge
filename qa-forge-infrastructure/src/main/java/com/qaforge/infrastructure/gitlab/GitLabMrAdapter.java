package com.qaforge.infrastructure.gitlab;

import com.qaforge.domain.exception.PrNotFoundException;
import com.qaforge.domain.model.ChangedFile;
import com.qaforge.domain.model.CodeDiff;
import com.qaforge.domain.model.PullRequest;
import com.qaforge.domain.port.out.MergeRequestPort;
import com.qaforge.infrastructure.gitlab.dto.GitLabDiffResponse;
import com.qaforge.infrastructure.gitlab.dto.GitLabMergeRequestResponse;
import com.qaforge.infrastructure.gitlab.dto.GitLabNoteResponse;
import com.qaforge.infrastructure.support.DiffTruncator;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Implements {@link MergeRequestPort} against the GitLab REST API v4 (PRD §12.10 G-11, §16.3). */
@Component
public class GitLabMrAdapter implements MergeRequestPort {

    private final RestClient restClient;

    public GitLabMrAdapter(RestClient.Builder restClientBuilder, GitLabProperties properties) {
        this.restClient = restClientBuilder
            .baseUrl(properties.baseUrl() + "/api/v4")
            .defaultHeader("PRIVATE-TOKEN", properties.token())
            .defaultHeader(HttpHeaders.ACCEPT, "application/json")
            .build();
    }

    @Override
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public PullRequest fetch(String projectPath, String mrIid) {
        String encodedProject = encode(projectPath);
        GitLabMergeRequestResponse mr = fetchRaw(projectPath, encodedProject, mrIid);
        List<String> comments = fetchNotes(encodedProject, mrIid);

        return new PullRequest(
            String.valueOf(mr.iid()),
            "gitlab",
            projectPath,
            mr.title(),
            mr.description(),
            mr.labels() == null ? List.of() : mr.labels(),
            mr.target_branch(),
            mr.source_branch(),
            mr.sha(),
            mr.author() == null ? null : mr.author().username(),
            comments,
            mr.web_url()
        );
    }

    @Override
    @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public CodeDiff fetchDiff(String projectPath, String mrIid) {
        String encodedProject = encode(projectPath);
        GitLabDiffResponse[] diffs = restClient.get()
            .uri("/projects/{id}/merge_requests/{iid}/diffs?per_page=100", encodedProject, mrIid)
            .retrieve()
            .body(GitLabDiffResponse[].class);

        List<GitLabDiffResponse> diffList = diffs == null ? List.of() : List.of(diffs);
        StringBuilder rawDiffBuilder = new StringBuilder();
        List<ChangedFile> changedFiles = diffList.stream().map(d -> {
            rawDiffBuilder.append(d.diff()).append('\n');
            return new ChangedFile(
                d.new_path() != null ? d.new_path() : d.old_path(),
                mapChangeType(d),
                countLinesStartingWith(d.diff(), '+'),
                countLinesStartingWith(d.diff(), '-'),
                d.diff());
        }).toList();

        DiffTruncator.Result truncated = DiffTruncator.truncate(rawDiffBuilder.toString());
        return new CodeDiff(truncated.diff(), changedFiles, truncated.truncated());
    }

    private GitLabMergeRequestResponse fetchRaw(String projectPath, String encodedProject, String mrIid) {
        try {
            return restClient.get()
                .uri("/projects/{id}/merge_requests/{iid}", encodedProject, mrIid)
                .retrieve()
                .body(GitLabMergeRequestResponse.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new PrNotFoundException(projectPath, mrIid);
            }
            throw e;
        }
    }

    private List<String> fetchNotes(String encodedProject, String mrIid) {
        try {
            GitLabNoteResponse[] notes = restClient.get()
                .uri("/projects/{id}/merge_requests/{iid}/notes?per_page=100", encodedProject, mrIid)
                .retrieve()
                .body(GitLabNoteResponse[].class);
            return notes == null ? List.of() : List.of(notes).stream()
                .filter(n -> !n.system())
                .map(GitLabNoteResponse::body)
                .toList();
        } catch (RestClientException e) {
            return List.of();
        }
    }

    private String mapChangeType(GitLabDiffResponse diff) {
        if (diff.new_file()) return "ADDED";
        if (diff.deleted_file()) return "DELETED";
        if (diff.renamed_file()) return "RENAMED";
        return "MODIFIED";
    }

    private int countLinesStartingWith(String diff, char prefix) {
        if (diff == null || diff.isEmpty()) {
            return 0;
        }
        return (int) diff.lines()
            .filter(line -> line.startsWith(String.valueOf(prefix)) && !line.startsWith(prefix == '+' ? "+++" : "---"))
            .count();
    }

    private String encode(String projectPath) {
        return URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
    }
}
