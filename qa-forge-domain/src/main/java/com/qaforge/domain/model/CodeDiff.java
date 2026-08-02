package com.qaforge.domain.model;

import java.util.List;

/** {@code truncated} is true when the raw diff exceeded the token budget (PRD C-02, 8,000 tokens). */
public record CodeDiff(
    String rawDiff,
    List<ChangedFile> changedFiles,
    boolean truncated
) {}
