package com.qaforge.infrastructure.vcs;

import com.qaforge.domain.model.PullRequest;
import com.qaforge.domain.port.out.VcsChecksPort;
import com.qaforge.infrastructure.github.GitHubChecksAdapter;
import com.qaforge.infrastructure.gitlab.GitLabChecksAdapter;
import org.springframework.stereotype.Component;

/**
 * The single {@link VcsChecksPort} bean, dispatching to {@link GitHubChecksAdapter} or
 * {@link GitLabChecksAdapter} by {@code PullRequest.vcsType()}. Two adapters can't both
 * implement {@link VcsChecksPort} directly — a constructor-injected {@code VcsChecksPort}
 * dependency (e.g. in {@code AgentOrchestrator}) would then be ambiguous, and PRD §9.2.1's
 * pipeline calls the same {@code vcsChecksPort} regardless of which VCS the PR came from.
 */
@Component
public class VcsChecksRouter implements VcsChecksPort {

    private final GitHubChecksAdapter gitHubChecksAdapter;
    private final GitLabChecksAdapter gitLabChecksAdapter;

    public VcsChecksRouter(GitHubChecksAdapter gitHubChecksAdapter, GitLabChecksAdapter gitLabChecksAdapter) {
        this.gitHubChecksAdapter = gitHubChecksAdapter;
        this.gitLabChecksAdapter = gitLabChecksAdapter;
    }

    @Override
    public void postPending(PullRequest pr, String checkName, String detailsUrl) {
        if (isGitlab(pr)) {
            gitLabChecksAdapter.postPending(pr, checkName, detailsUrl);
        } else {
            gitHubChecksAdapter.postPending(pr, checkName, detailsUrl);
        }
    }

    @Override
    public void postSuccess(PullRequest pr, String checkName, String summary) {
        if (isGitlab(pr)) {
            gitLabChecksAdapter.postSuccess(pr, checkName, summary);
        } else {
            gitHubChecksAdapter.postSuccess(pr, checkName, summary);
        }
    }

    @Override
    public void postFailure(PullRequest pr, String checkName, String summary) {
        if (isGitlab(pr)) {
            gitLabChecksAdapter.postFailure(pr, checkName, summary);
        } else {
            gitHubChecksAdapter.postFailure(pr, checkName, summary);
        }
    }

    private boolean isGitlab(PullRequest pr) {
        return "gitlab".equalsIgnoreCase(pr.vcsType());
    }
}
