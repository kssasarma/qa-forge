package com.qaforge.domain.model;

import java.util.List;

/** {@code source} is one of github_body, gitlab_body, jira, derived. */
public record AcceptanceCriteria(
    String source,
    String rawText,
    List<String> parsedCriteria
) {}
