package com.qaforge.application.agent;

import com.qaforge.application.agent.dto.TestPlanningResponse;
import com.qaforge.application.agent.support.LlmJsonCaller;
import com.qaforge.application.prompt.TestPlanningPrompts;
import com.qaforge.domain.model.ImpactAssessment;
import com.qaforge.domain.model.TestCase;
import com.qaforge.domain.model.TestScenario;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Turns an {@link ImpactAssessment} plus the existing registry into a bounded set of {@link TestScenario}s. */
@Service
public class TestPlanningAgent {

    private static final String AGENT_NAME = "TestPlanningAgent";
    private static final int MAX_SCENARIOS = 10;

    private final ChatClient chatClient;
    private final LlmJsonCaller llmJsonCaller;

    public TestPlanningAgent(ChatClient chatClient, LlmJsonCaller llmJsonCaller) {
        this.chatClient = chatClient;
        this.llmJsonCaller = llmJsonCaller;
    }

    public List<TestScenario> run(ImpactAssessment impactAssessment, List<TestCase> existingTests) {
        String userMessage = buildUserMessage(impactAssessment, existingTests);
        TestPlanningResponse response = llmJsonCaller.call(
            chatClient, AGENT_NAME, TestPlanningPrompts.SYSTEM, userMessage, TestPlanningResponse.class);
        List<TestScenario> scenarios = response.scenarios();
        return scenarios.size() > MAX_SCENARIOS ? scenarios.subList(0, MAX_SCENARIOS) : scenarios;
    }

    private String buildUserMessage(ImpactAssessment impactAssessment, List<TestCase> existingTests) {
        String impactJson = llmJsonCaller.toJson(impactAssessment, AGENT_NAME);
        String existingJson = llmJsonCaller.toJson(existingTests.stream().map(ExistingTestSummary::from).toList(), AGENT_NAME);

        return """
            ## ImpactAssessment
            %s

            ## Existing Test Cases
            %s
            """.formatted(impactJson, existingJson);
    }

    private record ExistingTestSummary(String fileName, String scenarioTitle, String userFlow, String layer) {
        static ExistingTestSummary from(TestCase testCase) {
            return new ExistingTestSummary(
                testCase.fileName(), testCase.scenarioTitle(), testCase.userFlow(), testCase.testLayer().name());
        }
    }
}
