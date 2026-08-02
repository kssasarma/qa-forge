package com.qaforge.domain.port.out;

import com.qaforge.domain.model.ExecutionResult;
import com.qaforge.domain.model.GeneratedTest;
import com.qaforge.domain.model.TestCase;
import java.util.List;

public interface TestExecutorPort {
    List<ExecutionResult> executeGenerated(List<GeneratedTest> tests, String baseUrl);
    List<ExecutionResult> executeExisting(List<TestCase> tests, String baseUrl);
}
