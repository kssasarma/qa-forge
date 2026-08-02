package com.qaforge.infrastructure.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.qaforge.domain.exception.PrNotFoundException;
import com.qaforge.domain.model.ChangedFile;
import com.qaforge.domain.model.CodeDiff;
import com.qaforge.domain.model.PullRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Maps GitHub REST API responses to domain records (PRD §19.1: "Mock RestClient... Assert correct mapping"). */
class GitHubPrAdapterTest {

    private final GitHubProperties properties = new GitHubProperties("https://api.github.com", "token123", "secret");

    @Test
    void fetchMapsGitHubPullRequestResponseToDomainPullRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubPrAdapter adapter = new GitHubPrAdapter(builder, properties);

        server.expect(requestTo("https://api.github.com/repos/acme/backend/pulls/1234"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer token123"))
            .andRespond(withSuccess("""
                {
                  "number": 1234,
                  "title": "Add checkout payment",
                  "body": "Implements card payment",
                  "labels": [{"name": "feature"}],
                  "base": {"ref": "main", "sha": "base-sha"},
                  "head": {"ref": "feature/payment", "sha": "head-sha"},
                  "user": {"login": "rohan"},
                  "html_url": "https://github.com/acme/backend/pull/1234"
                }
                """, MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://api.github.com/repos/acme/backend/issues/1234/comments?per_page=100"))
            .andRespond(withSuccess("""
                [{"body": "Please add tests"}]
                """, MediaType.APPLICATION_JSON));

        PullRequest pr = adapter.fetch("acme/backend", "1234");

        assertThat(pr.id()).isEqualTo("1234");
        assertThat(pr.vcsType()).isEqualTo("github");
        assertThat(pr.title()).isEqualTo("Add checkout payment");
        assertThat(pr.labels()).containsExactly("feature");
        assertThat(pr.baseBranch()).isEqualTo("main");
        assertThat(pr.headBranch()).isEqualTo("feature/payment");
        assertThat(pr.headSha()).isEqualTo("head-sha");
        assertThat(pr.authorLogin()).isEqualTo("rohan");
        assertThat(pr.reviewerComments()).containsExactly("Please add tests");

        server.verify();
    }

    @Test
    void fetchThrowsPrNotFoundExceptionOn404() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubPrAdapter adapter = new GitHubPrAdapter(builder, properties);

        server.expect(requestTo("https://api.github.com/repos/acme/backend/pulls/9999"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.fetch("acme/backend", "9999"))
            .isInstanceOf(PrNotFoundException.class);
    }

    @Test
    void fetchDiffMapsChangedFilesAndChangeTypes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubPrAdapter adapter = new GitHubPrAdapter(builder, properties);

        server.expect(requestTo("https://api.github.com/repos/acme/backend/pulls/1234"))
            .andRespond(withSuccess("diff --git a/x b/x\n+added line", MediaType.parseMediaType("application/vnd.github.v3.diff")));

        server.expect(requestTo("https://api.github.com/repos/acme/backend/pulls/1234/files?per_page=100"))
            .andRespond(withSuccess("""
                [
                  {"filename": "src/new.ts", "status": "added", "additions": 5, "deletions": 0, "patch": "p1"},
                  {"filename": "src/old.ts", "status": "removed", "additions": 0, "deletions": 10, "patch": "p2"},
                  {"filename": "src/renamed.ts", "status": "renamed", "additions": 1, "deletions": 1, "patch": "p3"}
                ]
                """, MediaType.APPLICATION_JSON));

        CodeDiff diff = adapter.fetchDiff("acme/backend", "1234");

        assertThat(diff.rawDiff()).contains("added line");
        assertThat(diff.truncated()).isFalse();
        assertThat(diff.changedFiles()).extracting(ChangedFile::filePath, ChangedFile::changeType)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("src/new.ts", "ADDED"),
                org.assertj.core.groups.Tuple.tuple("src/old.ts", "DELETED"),
                org.assertj.core.groups.Tuple.tuple("src/renamed.ts", "RENAMED"));
    }
}
