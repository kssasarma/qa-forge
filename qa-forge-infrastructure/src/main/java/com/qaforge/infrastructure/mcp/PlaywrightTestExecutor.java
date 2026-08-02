package com.qaforge.infrastructure.mcp;

import com.qaforge.application.agent.TestExecutionAgent;
import com.qaforge.domain.model.ExecutionResult;
import com.qaforge.domain.model.GeneratedTest;
import com.qaforge.domain.model.TestCase;
import com.qaforge.domain.port.out.TestExecutorPort;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Implements {@link TestExecutorPort} (PRD §8 project structure) by delegating to
 * {@code TestExecutionAgent}, the actual Playwright-MCP-driving agent.
 *
 * <p>{@code AgentOrchestrator} calls {@code TestExecutionAgent} directly rather than through
 * this port: self-healing needs to return the corrected source of a healed test so it can be
 * written back to disk/the registry, and {@link TestExecutorPort#executeGenerated} — fixed by
 * PRD §9.1.2 to return only {@code List<ExecutionResult>} — has no way to carry that. This
 * adapter exists to satisfy the port contract itself (e.g. for callers that only need results,
 * not healed sources) and to keep {@code TestExecutionAgent} genuinely swappable behind
 * {@link TestExecutorPort} if a future execution engine replaces it.
 */
@Component
public class PlaywrightTestExecutor implements TestExecutorPort {

    private final TestExecutionAgent testExecutionAgent;

    public PlaywrightTestExecutor(TestExecutionAgent testExecutionAgent) {
        this.testExecutionAgent = testExecutionAgent;
    }

    @Override
    public List<ExecutionResult> executeGenerated(List<GeneratedTest> tests, String baseUrl) {
        return testExecutionAgent.execute(tests, baseUrl).results();
    }

    @Override
    public List<ExecutionResult> executeExisting(List<TestCase> tests, String baseUrl) {
        return testExecutionAgent.executeExisting(tests, baseUrl);
    }
}
