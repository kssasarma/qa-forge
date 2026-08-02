package com.qaforge.application.agent;

import com.qaforge.application.agent.support.LlmJsonCaller;
import com.qaforge.application.agent.support.NamingUtil;
import com.qaforge.application.prompt.DbValidationGenerationPrompts;
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

    public DbValidationGenerationAgent(ChatClient chatClient, LlmJsonCaller llmJsonCaller) {
        this.chatClient = chatClient;
        this.llmJsonCaller = llmJsonCaller;
    }

    public GeneratedTest generate(TestScenario scenario) {
        String userMessage = llmJsonCaller.toJson(scenario, AGENT_NAME) + "\n\n## Table\n" + scenario.dbTable();
        String content = llmJsonCaller.callForText(chatClient, AGENT_NAME, DbValidationGenerationPrompts.SYSTEM, userMessage);
        String fileName = NamingUtil.pascalCase(scenario.dbTable()) + "DbValidationTest.java";
        return new GeneratedTest(scenario.id(), fileName, content, TestLayer.DB_VALIDATION, List.of(scenario.userFlow()));
    }
}
