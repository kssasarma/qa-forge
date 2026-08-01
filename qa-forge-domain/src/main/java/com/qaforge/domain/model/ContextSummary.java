package com.qaforge.domain.model;

import java.util.List;

/** Output of {@code ContextGatheringAgent}. {@code estimatedRisk} is LOW, MEDIUM, or HIGH. */
public record ContextSummary(
    String prSummary,
    List<String> changedAreas,
    List<String> acceptanceCriteriaLines,
    List<String> reviewerConcerns,
    String estimatedRisk
) {}
