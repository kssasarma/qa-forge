package com.qaforge.application.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.qaforge.application.agent.dto.TestPlanningResponse;
import com.qaforge.application.agent.support.LlmJsonCaller;
import com.qaforge.domain.model.ImpactAssessment;
import com.qaforge.domain.model.ScenarioType;
import com.qaforge.domain.model.TestLayer;
import com.qaforge.domain.model.TestScenario;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

/** PRD §9.2.4: "Limit to 10 scenarios maximum per request." */
@ExtendWith(MockitoExtension.class)
class TestPlanningAgentTest {

    @Mock
    private ChatClient chatClient;
    @Mock
    private LlmJsonCaller llmJsonCaller;

    @Test
    void truncatesToTenScenariosEvenWhenLlmReturnsMore() {
        TestPlanningAgent agent = new TestPlanningAgent(chatClient, llmJsonCaller);

        List<TestScenario> fifteenScenarios = IntStream.range(0, 15)
            .mapToObj(i -> scenario("Scenario " + i))
            .toList();

        when(llmJsonCaller.call(any(), anyString(), anyString(), anyString(), eq(TestPlanningResponse.class)))
            .thenReturn(new TestPlanningResponse(fifteenScenarios));

        ImpactAssessment impact = new ImpactAssessment(List.of(), List.of(), false, List.of(), List.of(), List.of());
        List<TestScenario> result = agent.run(impact, List.of());

        assertThat(result).hasSize(10);
        assertThat(result.get(0).title()).isEqualTo("Scenario 0");
    }

    @Test
    void passesThroughFewerThanTenScenariosUnchanged() {
        TestPlanningAgent agent = new TestPlanningAgent(chatClient, llmJsonCaller);
        List<TestScenario> threeScenarios = List.of(scenario("A"), scenario("B"), scenario("C"));

        when(llmJsonCaller.call(any(), anyString(), anyString(), anyString(), eq(TestPlanningResponse.class)))
            .thenReturn(new TestPlanningResponse(threeScenarios));

        ImpactAssessment impact = new ImpactAssessment(List.of(), List.of(), false, List.of(), List.of(), List.of());
        List<TestScenario> result = agent.run(impact, List.of());

        assertThat(result).hasSize(3);
    }

    private TestScenario scenario(String title) {
        return new TestScenario(
            UUID.randomUUID().toString(), title, "description", "Checkout", ScenarioType.NEW,
            TestLayer.PLAYWRIGHT, List.of("step 1"), "/checkout", false, null, null);
    }
}
