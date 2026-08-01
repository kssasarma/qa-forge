package com.qaforge.application.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaforge.application.agent.support.LlmJsonCaller;
import com.qaforge.application.agent.support.NamingUtil;
import com.qaforge.application.prompt.RestAssuredGenerationPrompts;
import com.qaforge.domain.exception.LlmParseException;
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
    private final ObjectMapper objectMapper;
    private final OpenApiSpecPort openApiSpecPort;

    public RestAssuredGenerationAgent(ChatClient chatClient, LlmJsonCaller llmJsonCaller,
                                       ObjectMapper objectMapper, OpenApiSpecPort openApiSpecPort) {
        this.chatClient = chatClient;
        this.llmJsonCaller = llmJsonCaller;
        this.objectMapper = objectMapper;
        this.openApiSpecPort = openApiSpecPort;
    }

    public GeneratedTest generate(TestScenario scenario, String openApiSpecUrl) {
        String operationJson = scenario.openApiOperationId() == null || openApiSpecUrl == null
            ? "(no OpenAPI operation available)"
            : openApiSpecPort.fetchOperation(openApiSpecUrl, scenario.openApiOperationId())
                .orElse("(operation " + scenario.openApiOperationId() + " not found in spec)");

        String userMessage = serializeScenario(scenario) + "\n\n## OpenAPI Operation\n" + operationJson;
        String content = llmJsonCaller.callForText(chatClient, AGENT_NAME, RestAssuredGenerationPrompts.SYSTEM, userMessage);
        String fileName = NamingUtil.pascalCase(scenario.title()) + "Test.java";
        return new GeneratedTest(scenario.id(), fileName, content, TestLayer.REST_ASSURED, List.of(scenario.userFlow()));
    }

    private String serializeScenario(TestScenario scenario) {
        try {
            return objectMapper.writeValueAsString(scenario);
        } catch (Exception e) {
            throw new LlmParseException(AGENT_NAME, "scenario serialization failure", e);
        }
    }
}
