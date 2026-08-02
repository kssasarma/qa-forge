package com.qaforge.application.agent;

import com.qaforge.application.agent.support.LlmJsonCaller;
import com.qaforge.application.prompt.ExecutionPrompts;
import com.qaforge.domain.model.ExecutionResult;
import com.qaforge.domain.model.ExecutionStatus;
import com.qaforge.domain.model.GeneratedTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.stereotype.Service;

/**
 * Rewrites the single failing locator line of a Playwright test using the live accessibility
 * tree, per PRD §9.2.9. The healed source replaces the original on disk and in the registry,
 * with {@code retryCount = 1}.
 */
@Service
public class SelfHealingLocatorAgent {

    private static final Logger log = LoggerFactory.getLogger(SelfHealingLocatorAgent.class);
    private static final String AGENT_NAME = "SelfHealingLocatorAgent";

    private final ChatClient chatClient;
    private final LlmJsonCaller llmJsonCaller;
    private final SyncMcpToolCallbackProvider playwrightToolProvider;

    public SelfHealingLocatorAgent(ChatClient chatClient, LlmJsonCaller llmJsonCaller,
                                    SyncMcpToolCallbackProvider playwrightToolProvider) {
        this.chatClient = chatClient;
        this.llmJsonCaller = llmJsonCaller;
        this.playwrightToolProvider = playwrightToolProvider;
    }

    /**
     * Attempts to heal the failing locator. On success, {@link HealingOutcome#healedTest()}
     * carries the corrected source (the caller persists it to disk and the registry) and the
     * result flips to PASSED with {@code retryCount = 1}. On failure, the original result is
     * returned unchanged except for {@code retryCount = 1}, and {@code healedTest} is null.
     */
    public HealingOutcome heal(GeneratedTest failingTest, ExecutionResult failureResult, String baseUrl) {
        String userMessage = """
            ## Failing test source (%s)
            %s

            ## Error message
            %s

            ## Target base URL
            %s
            """.formatted(failingTest.fileName(), failingTest.content(), failureResult.errorMessage(), baseUrl);

        try {
            String healedSource = llmJsonCaller.callForText(
                chatClient.mutate().defaultToolCallbacks(playwrightToolProvider).build(),
                AGENT_NAME, ExecutionPrompts.SELF_HEAL_SYSTEM, userMessage);

            GeneratedTest healedTest = new GeneratedTest(
                failingTest.scenarioId(), failingTest.fileName(), healedSource,
                failingTest.layer(), failingTest.tags());
            ExecutionResult healedResult = new ExecutionResult(
                failureResult.scenarioId(), failureResult.fileName(), ExecutionStatus.PASSED,
                null, failureResult.durationMs(), 1);
            return new HealingOutcome(healedResult, healedTest);
        } catch (RuntimeException e) {
            log.warn("Self-healing failed for {}: {}", failingTest.fileName(), e.getMessage());
            ExecutionResult unhealedResult = new ExecutionResult(
                failureResult.scenarioId(), failureResult.fileName(), failureResult.status(),
                failureResult.errorMessage(), failureResult.durationMs(), 1);
            return new HealingOutcome(unhealedResult, null);
        }
    }

    public record HealingOutcome(ExecutionResult result, GeneratedTest healedTest) {
        public boolean healed() {
            return healedTest != null;
        }
    }
}
