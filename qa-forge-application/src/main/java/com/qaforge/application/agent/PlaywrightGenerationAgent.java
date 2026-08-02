package com.qaforge.application.agent;

import com.qaforge.application.agent.support.LlmJsonCaller;
import com.qaforge.application.agent.support.NamingUtil;
import com.qaforge.application.prompt.PlaywrightGenerationPrompts;
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

    public PlaywrightGenerationAgent(ChatClient chatClient, LlmJsonCaller llmJsonCaller) {
        this.chatClient = chatClient;
        this.llmJsonCaller = llmJsonCaller;
    }

    public GeneratedTest generate(TestScenario scenario, String prNumber) {
        String userMessage = llmJsonCaller.toJson(scenario, AGENT_NAME);
        String content = llmJsonCaller.callForText(chatClient, AGENT_NAME, PlaywrightGenerationPrompts.SYSTEM, userMessage);
        String fileName = NamingUtil.snakeCase(scenario.title()) + "_pr" + prNumber + ".spec.ts";
        List<String> tags = List.of(NamingUtil.snakeCase(scenario.userFlow()).replace('_', '-'), "pr-" + prNumber);
        return new GeneratedTest(scenario.id(), fileName, content, TestLayer.PLAYWRIGHT, tags);
    }
}
