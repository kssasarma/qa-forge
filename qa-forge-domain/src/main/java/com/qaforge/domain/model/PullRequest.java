package com.qaforge.domain.model;

import java.util.List;

/** A pull request (GitHub) or merge request (GitLab), normalized to a single shape. */
public record PullRequest(
    String id,
    String vcsType,
    String repositoryFullName,
    String title,
    String description,
    List<String> labels,
    String baseBranch,
    String headBranch,
    String headSha,
    String authorLogin,
    List<String> reviewerComments,
    String webUrl
) {}
