package com.qaforge.domain.port.out;

import com.qaforge.domain.model.CodeDiff;
import com.qaforge.domain.model.PullRequest;

/** GitLab equivalent of {@link PullRequestPort}. */
public interface MergeRequestPort {
    PullRequest fetch(String projectPath, String mrIid);
    CodeDiff fetchDiff(String projectPath, String mrIid);
}
