package com.qaforge.domain.model;

import java.util.List;

/** Output of {@code CodeAnalysisAgent}. */
public record ImpactAssessment(
    List<ImpactedFlow> impactedUserFlows,
    List<String> safeToSkipFlows,
    boolean backendOnlyChange,
    List<String> uiPagesAffected,
    List<String> newApiEndpoints,
    List<String> changedDbTables
) {}
