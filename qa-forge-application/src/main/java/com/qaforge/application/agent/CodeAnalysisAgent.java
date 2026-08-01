package com.qaforge.application.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaforge.application.agent.support.LlmJsonCaller;
import com.qaforge.application.prompt.CodeAnalysisPrompts;
import com.qaforge.domain.exception.LlmParseException;
import com.qaforge.domain.model.CodeDiff;
import com.qaforge.domain.model.ContextSummary;
import com.qaforge.domain.model.ImpactAssessment;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Assesses which user flows, UI pages, API endpoints, and DB tables a diff impacts. */
@Service
public class CodeAnalysisAgent {

    private static final String AGENT_NAME = "CodeAnalysisAgent";

    private final ChatClient chatClient;
    private final LlmJsonCaller llmJsonCaller;
    private final ObjectMapper objectMapper;

    public CodeAnalysisAgent(ChatClient chatClient, LlmJsonCaller llmJsonCaller, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.llmJsonCaller = llmJsonCaller;
        this.objectMapper = objectMapper;
    }

    public ImpactAssessment run(ContextSummary contextSummary, CodeDiff diff) {
        String userMessage = buildUserMessage(contextSummary, diff);
        return llmJsonCaller.call(chatClient, AGENT_NAME, CodeAnalysisPrompts.SYSTEM, userMessage, ImpactAssessment.class);
    }

    private String buildUserMessage(ContextSummary contextSummary, CodeDiff diff) {
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(contextSummary);
        } catch (JsonProcessingException e) {
            throw new LlmParseException(AGENT_NAME, String.valueOf(contextSummary), e);
        }

        return """
            ## ContextSummary
            %s

            ## Raw Diff (truncated=%s)
            %s
            """.formatted(contextJson, diff.truncated(), diff.rawDiff());
    }
}
