package com.qaforge.application.agent;

import com.qaforge.application.agent.support.LlmJsonCaller;
import com.qaforge.application.prompt.ContextGatheringPrompts;
import com.qaforge.domain.model.AcceptanceCriteria;
import com.qaforge.domain.model.ChangedFile;
import com.qaforge.domain.model.CodeDiff;
import com.qaforge.domain.model.ContextSummary;
import com.qaforge.domain.model.PullRequest;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Reads PR metadata and acceptance criteria and summarises them for downstream agents. */
@Service
public class ContextGatheringAgent {

    private static final String AGENT_NAME = "ContextGatheringAgent";

    private final ChatClient chatClient;
    private final LlmJsonCaller llmJsonCaller;

    public ContextGatheringAgent(ChatClient chatClient, LlmJsonCaller llmJsonCaller) {
        this.chatClient = chatClient;
        this.llmJsonCaller = llmJsonCaller;
    }

    public ContextSummary run(PullRequest pr, CodeDiff diff, Optional<AcceptanceCriteria> acceptanceCriteria) {
        String userMessage = buildUserMessage(pr, diff, acceptanceCriteria);
        return llmJsonCaller.call(chatClient, AGENT_NAME, ContextGatheringPrompts.SYSTEM, userMessage, ContextSummary.class);
    }

    private String buildUserMessage(PullRequest pr, CodeDiff diff, Optional<AcceptanceCriteria> ac) {
        String changedFilesSummary = diff.changedFiles().stream()
            .map(this::describeChangedFile)
            .collect(Collectors.joining("\n"));

        String reviewerComments = pr.reviewerComments() == null || pr.reviewerComments().isEmpty()
            ? "(none)"
            : String.join("\n", pr.reviewerComments());

        String acText = ac.map(AcceptanceCriteria::rawText).filter(t -> !t.isBlank()).orElse("(none provided)");

        return """
            ## Pull Request Title
            %s

            ## Pull Request Description
            %s

            ## Reviewer Comments
            %s

            ## Changed Files (%d, truncated=%s)
            %s

            ## Acceptance Criteria
            %s
            """.formatted(
                pr.title(),
                nullToEmpty(pr.description()),
                reviewerComments,
                diff.changedFiles().size(),
                diff.truncated(),
                changedFilesSummary,
                acText
            );
    }

    private String describeChangedFile(ChangedFile file) {
        return "- [%s] %s (+%d/-%d)".formatted(
            file.changeType(), file.filePath(), file.linesAdded(), file.linesDeleted());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
