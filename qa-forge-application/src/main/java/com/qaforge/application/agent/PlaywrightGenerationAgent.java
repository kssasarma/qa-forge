package com.qaforge.application.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaforge.application.agent.support.LlmJsonCaller;
import com.qaforge.application.agent.support.NamingUtil;
import com.qaforge.application.prompt.PlaywrightGenerationPrompts;
import com.qaforge.domain.exception.LlmParseException;
import com.qaforge.domain.model.GeneratedTest;
import com.qaforge.domain.model.TestLayer;
import com.qaforge.domain.model.TestScenario;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Generates a Playwright TypeScript spec file for a single {@code PLAYWRIGHT} scenario. */
@Service
public class PlaywrightGenerationAgent {

    private static final String AGENT_NAME = "PlaywrightGenerationAgent";

    private final ChatClient chatClient;
    private final LlmJsonCaller llmJsonCaller;
    private final ObjectMapper objectMapper;

    public PlaywrightGenerationAgent(ChatClient chatClient, LlmJsonCaller llmJsonCaller, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.llmJsonCaller = llmJsonCaller;
        this.objectMapper = objectMapper;
    }

    public GeneratedTest generate(TestScenario scenario, String prNumber) {
        String userMessage = serializeScenario(scenario);
        String content = llmJsonCaller.callForText(chatClient, AGENT_NAME, PlaywrightGenerationPrompts.SYSTEM, userMessage);
        String fileName = NamingUtil.snakeCase(scenario.title()) + "_pr" + prNumber + ".spec.ts";
        List<String> tags = List.of(NamingUtil.snakeCase(scenario.userFlow()).replace('_', '-'), "pr-" + prNumber);
        return new GeneratedTest(scenario.id(), fileName, content, TestLayer.PLAYWRIGHT, tags);
    }

    private String serializeScenario(TestScenario scenario) {
        try {
            return objectMapper.writeValueAsString(scenario);
        } catch (Exception e) {
            throw new LlmParseException(AGENT_NAME, "scenario serialization failure", e);
        }
    }
}
