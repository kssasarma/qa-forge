package com.qaforge.application.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaforge.application.agent.support.LlmJsonCaller;
import com.qaforge.application.agent.support.NamingUtil;
import com.qaforge.application.prompt.DbValidationGenerationPrompts;
import com.qaforge.domain.exception.LlmParseException;
import com.qaforge.domain.model.GeneratedTest;
import com.qaforge.domain.model.TestLayer;
import com.qaforge.domain.model.TestScenario;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Generates a JDBC-based DB validation JUnit 5 test class for a single {@code DB_VALIDATION} scenario. */
@Service
public class DbValidationGenerationAgent {

    private static final String AGENT_NAME = "DbValidationGenerationAgent";

    private final ChatClient chatClient;
    private final LlmJsonCaller llmJsonCaller;
    private final ObjectMapper objectMapper;

    public DbValidationGenerationAgent(ChatClient chatClient, LlmJsonCaller llmJsonCaller, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.llmJsonCaller = llmJsonCaller;
        this.objectMapper = objectMapper;
    }

    public GeneratedTest generate(TestScenario scenario) {
        String userMessage = serializeScenario(scenario) + "\n\n## Table\n" + scenario.dbTable();
        String content = llmJsonCaller.callForText(chatClient, AGENT_NAME, DbValidationGenerationPrompts.SYSTEM, userMessage);
        String fileName = NamingUtil.pascalCase(scenario.dbTable()) + "DbValidationTest.java";
        return new GeneratedTest(scenario.id(), fileName, content, TestLayer.DB_VALIDATION, List.of(scenario.userFlow()));
    }

    private String serializeScenario(TestScenario scenario) {
        try {
            return objectMapper.writeValueAsString(scenario);
        } catch (Exception e) {
            throw new LlmParseException(AGENT_NAME, "scenario serialization failure", e);
        }
    }
}
