package com.qaforge.application.agent;

import com.qaforge.application.agent.support.LlmJsonCaller;
import com.qaforge.application.prompt.CodeAnalysisPrompts;
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

    public CodeAnalysisAgent(ChatClient chatClient, LlmJsonCaller llmJsonCaller) {
        this.chatClient = chatClient;
        this.llmJsonCaller = llmJsonCaller;
    }

    public ImpactAssessment run(ContextSummary contextSummary, CodeDiff diff) {
        String userMessage = buildUserMessage(contextSummary, diff);
        return llmJsonCaller.call(chatClient, AGENT_NAME, CodeAnalysisPrompts.SYSTEM, userMessage, ImpactAssessment.class);
    }

    private String buildUserMessage(ContextSummary contextSummary, CodeDiff diff) {
        String contextJson = llmJsonCaller.toJson(contextSummary, AGENT_NAME);

        return """
            ## ContextSummary
            %s

            ## Raw Diff (truncated=%s)
            %s
            """.formatted(contextJson, diff.truncated(), diff.rawDiff());
    }
}
