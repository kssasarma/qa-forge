package com.qaforge.domain.port.out;

import com.qaforge.domain.model.CodeDiff;
import com.qaforge.domain.model.PullRequest;

public interface PullRequestPort {
    PullRequest fetch(String repositoryFullName, String prNumber);
    CodeDiff fetchDiff(String repositoryFullName, String prNumber);
}
