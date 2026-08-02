package com.qaforge.application.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qaforge.application.agent.support.LlmJsonCaller;
import com.qaforge.domain.model.ChangedFile;
import com.qaforge.domain.model.CodeDiff;
import com.qaforge.domain.model.ContextSummary;
import com.qaforge.domain.model.PullRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
class ContextGatheringAgentTest {

    @Mock
    private ChatClient chatClient;
    @Mock
    private LlmJsonCaller llmJsonCaller;

    @Test
    void buildsUserMessageFromPrAndDiffAndDelegatesParsingToLlmJsonCaller() {
        ContextGatheringAgent agent = new ContextGatheringAgent(chatClient, llmJsonCaller);

        PullRequest pr = new PullRequest(
            "1234", "github", "acme/backend", "Add checkout payment step",
            "Implements card payment", List.of(), "main", "feature/payment", "abc123",
            "rohan", List.of("Please add tests"), "https://github.com/acme/backend/pull/1234");
        CodeDiff diff = new CodeDiff("diff --git a/x b/x",
            List.of(new ChangedFile("src/Checkout.tsx", "MODIFIED", 10, 2, "patch")), false);

        ContextSummary expected = new ContextSummary(
            "Adds card payment to checkout", List.of("Checkout > Payment"), List.of(), List.of(), "HIGH");
        when(llmJsonCaller.call(any(), anyString(), anyString(), anyString(), eq(ContextSummary.class)))
            .thenReturn(expected);

        ContextSummary result = agent.run(pr, diff, Optional.empty());

        assertThat(result).isEqualTo(expected);

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmJsonCaller).call(
            eq(chatClient), eq("ContextGatheringAgent"), anyString(), userMessageCaptor.capture(), eq(ContextSummary.class));

        String userMessage = userMessageCaptor.getValue();
        assertThat(userMessage).contains("Add checkout payment step");
        assertThat(userMessage).contains("Please add tests");
        assertThat(userMessage).contains("src/Checkout.tsx");
        assertThat(userMessage).contains("(none provided)");
    }
}
