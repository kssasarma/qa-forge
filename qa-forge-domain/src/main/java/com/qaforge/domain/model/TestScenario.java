package com.qaforge.domain.model;

import java.util.List;

/**
 * Output of {@code TestPlanningAgent}. {@code openApiOperationId} is null unless
 * {@code layer == REST_ASSURED}; {@code dbTable} is null unless {@code layer == DB_VALIDATION}.
 */
public record TestScenario(
    String id,
    String title,
    String description,
    String userFlow,
    ScenarioType type,
    TestLayer layer,
    List<String> steps,
    String targetPath,
    boolean requiresAuth,
    String openApiOperationId,
    String dbTable
) {}
