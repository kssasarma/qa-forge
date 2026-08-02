package com.qaforge.application.agent;

import com.qaforge.application.agent.dto.ExecutionResultsResponse;
import com.qaforge.application.agent.support.LlmJsonCaller;
import com.qaforge.application.prompt.ExecutionPrompts;
import com.qaforge.domain.model.ExecutionResult;
import com.qaforge.domain.model.ExecutionStatus;
import com.qaforge.domain.model.GeneratedTest;
import com.qaforge.domain.model.TestCase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.stereotype.Service;

/**
 * Executes {@code PLAYWRIGHT}-layer tests via the Playwright MCP server (PRD §9.2.8).
 * RestAssured and DB validation tests are written to disk only, never executed here.
 */
@Service
public class TestExecutionAgent {

    private static final Logger log = LoggerFactory.getLogger(TestExecutionAgent.class);
    private static final String AGENT_NAME = "TestExecutionAgent";
    private static final int EXISTING_TEST_BATCH_SIZE = 20;
    private static final List<String> HEALABLE_ERROR_MARKERS =
        List.of("Element not found", "strict mode violation");

    private final ChatClient chatClient;
    private final LlmJsonCaller llmJsonCaller;
    private final SyncMcpToolCallbackProvider playwrightToolProvider;
    private final SelfHealingLocatorAgent selfHealingLocatorAgent;

    public TestExecutionAgent(ChatClient chatClient, LlmJsonCaller llmJsonCaller,
                               SyncMcpToolCallbackProvider playwrightToolProvider,
                               SelfHealingLocatorAgent selfHealingLocatorAgent) {
        this.chatClient = chatClient;
        this.llmJsonCaller = llmJsonCaller;
        this.playwrightToolProvider = playwrightToolProvider;
        this.selfHealingLocatorAgent = selfHealingLocatorAgent;
    }

    /**
     * Executes newly generated Playwright tests and self-heals any locator failures in place.
     * Returns the final results plus a map of fileName → healed source for every test that was
     * successfully self-healed, so the caller can overwrite the on-disk/registry copy.
     */
    public ExecutionOutcome execute(List<GeneratedTest> playwrightTests, String baseUrl) {
        if (playwrightTests.isEmpty()) {
            return new ExecutionOutcome(List.of(), Map.of());
        }
        List<ExecutionResult> results = new ArrayList<>(executeBatch(describeGenerated(playwrightTests), baseUrl));
        Map<String, GeneratedTest> healedTests = new HashMap<>();

        for (int i = 0; i < results.size() && i < playwrightTests.size(); i++) {
            ExecutionResult result = results.get(i);
            if (isHealable(result)) {
                GeneratedTest original = playwrightTests.get(i);
                SelfHealingLocatorAgent.HealingOutcome outcome = selfHealingLocatorAgent.heal(original, result, baseUrl);
                results.set(i, outcome.result());
                if (outcome.healed()) {
                    healedTests.put(outcome.healedTest().fileName(), outcome.healedTest());
                }
            }
        }
        return new ExecutionOutcome(results, healedTests);
    }

    public record ExecutionOutcome(List<ExecutionResult> results, Map<String, GeneratedTest> healedTests) {}

    /** Executes the active registry suite (regression run), batched per PRD §22 C-04. */
    public List<ExecutionResult> executeExisting(List<TestCase> existingTests, String baseUrl) {
        List<ExecutionResult> results = new ArrayList<>();
        for (int start = 0; start < existingTests.size(); start += EXISTING_TEST_BATCH_SIZE) {
            List<TestCase> batch = existingTests.subList(start, Math.min(start + EXISTING_TEST_BATCH_SIZE, existingTests.size()));
            results.addAll(executeBatch(describeExisting(batch), baseUrl));
        }
        return results;
    }

    private List<ExecutionResult> executeBatch(String testDescriptions, String baseUrl) {
        String userMessage = """
            ## Target base URL
            %s

            ## Tests to execute
            %s
            """.formatted(baseUrl, testDescriptions);

        try {
            ExecutionResultsResponse response = llmJsonCaller.call(
                chatClient.mutate().defaultToolCallbacks(playwrightToolProvider).build(),
                AGENT_NAME, ExecutionPrompts.EXECUTE_SYSTEM, userMessage, ExecutionResultsResponse.class);
            return response.results();
        } catch (RuntimeException e) {
            log.error("Playwright MCP execution batch failed", e);
            return List.of();
        }
    }

    private String describeGenerated(List<GeneratedTest> tests) {
        return tests.stream()
            .map(t -> "### %s (scenario:%s, tags:%s)\n%s".formatted(
                t.fileName(), t.scenarioId(), t.tags(), t.content()))
            .collect(Collectors.joining("\n\n"));
    }

    private String describeExisting(List<TestCase> tests) {
        return tests.stream()
            .map(t -> "### %s (scenario:%s, targetPath:%s, requiresAuth:%s)".formatted(
                t.fileName(), t.scenarioId(), t.targetPath(), t.requiresAuth()))
            .collect(Collectors.joining("\n"));
    }

    private boolean isHealable(ExecutionResult result) {
        if (result.status() != ExecutionStatus.FAILED || result.errorMessage() == null) {
            return false;
        }
        return HEALABLE_ERROR_MARKERS.stream().anyMatch(result.errorMessage()::contains);
    }
}
