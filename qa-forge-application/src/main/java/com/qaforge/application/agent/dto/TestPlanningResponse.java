package com.qaforge.application.agent.dto;

import com.qaforge.domain.model.TestScenario;
import java.util.List;

/** Wrapper for {@code TestPlanningAgent}'s {@code {"scenarios": [...]}} JSON shape. */
public record TestPlanningResponse(List<TestScenario> scenarios) {}
