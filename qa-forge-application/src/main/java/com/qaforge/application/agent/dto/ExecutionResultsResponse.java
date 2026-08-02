package com.qaforge.application.agent.dto;

import com.qaforge.domain.model.ExecutionResult;
import java.util.List;

/** Wrapper for {@code TestExecutionAgent}'s {@code {"results": [...]}} JSON shape. */
public record ExecutionResultsResponse(List<ExecutionResult> results) {}
