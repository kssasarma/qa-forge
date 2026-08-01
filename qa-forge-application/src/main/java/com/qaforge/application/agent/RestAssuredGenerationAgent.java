package com.qaforge.application.agent;

import com.qaforge.application.agent.support.LlmJsonCaller;
import com.qaforge.application.agent.support.NamingUtil;
import com.qaforge.application.prompt.RestAssuredGenerationPrompts;
import com.qaforge.domain.model.GeneratedTest;
import com.qaforge.domain.model.TestLayer;
import com.qaforge.domain.model.TestScenario;
import com.qaforge.domain.port.out.OpenApiSpecPort;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Generates a RestAssured JUnit 5 test class for a single {@code REST_ASSURED} scenario. */
@Service
public class RestAssuredGenerationAgent {

    private static final String AGENT_NAME = "RestAssuredGenerationAgent";

    private final ChatClient chatClient;
    private final LlmJsonCaller llmJsonCaller;
    private final OpenApiSpecPort openApiSpecPort;

    public RestAssuredGenerationAgent(ChatClient chatClient, LlmJsonCaller llmJsonCaller, OpenApiSpecPort openApiSpecPort) {
        this.chatClient = chatClient;
        this.llmJsonCaller = llmJsonCaller;
        this.openApiSpecPort = openApiSpecPort;
    }

    public GeneratedTest generate(TestScenario scenario, String openApiSpecUrl) {
        String operationJson = scenario.openApiOperationId() == null || openApiSpecUrl == null
            ? "(no OpenAPI operation available)"
            : openApiSpecPort.fetchOperation(openApiSpecUrl, scenario.openApiOperationId())
                .orElse("(operation " + scenario.openApiOperationId() + " not found in spec)");

        String userMessage = llmJsonCaller.toJson(scenario, AGENT_NAME) + "\n\n## OpenAPI Operation\n" + operationJson;
        String content = llmJsonCaller.callForText(chatClient, AGENT_NAME, RestAssuredGenerationPrompts.SYSTEM, userMessage);
        String fileName = NamingUtil.pascalCase(scenario.title()) + "Test.java";
        return new GeneratedTest(scenario.id(), fileName, content, TestLayer.REST_ASSURED, List.of(scenario.userFlow()));
    }
}
