package com.qaforge.application.agent.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qaforge.domain.exception.LlmParseException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

/** Covers the two-attempt JSON parse/retry policy from PRD §10.3/§16.2. */
class LlmJsonCallerTest {

    private record Sample(String value) {}

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;
    private LlmJsonCaller llmJsonCaller;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        llmJsonCaller = new LlmJsonCaller(new SimpleMeterRegistry());
    }

    @Test
    void parsesValidJsonOnFirstAttempt() {
        when(callResponseSpec.content()).thenReturn("{\"value\":\"ok\"}");

        Sample result = llmJsonCaller.call(chatClient, "TestAgent", "system", "user", Sample.class);

        assertThat(result.value()).isEqualTo("ok");
        verify(requestSpec, times(1)).system(anyString());
    }

    @Test
    void stripsMarkdownCodeFences() {
        when(callResponseSpec.content()).thenReturn("```json\n{\"value\":\"fenced\"}\n```");

        Sample result = llmJsonCaller.call(chatClient, "TestAgent", "system", "user", Sample.class);

        assertThat(result.value()).isEqualTo("fenced");
    }

    @Test
    void retriesOnceWithStricterPromptWhenFirstResponseIsUnparseable() {
        when(callResponseSpec.content())
            .thenReturn("not json at all")
            .thenReturn("{\"value\":\"recovered\"}");

        Sample result = llmJsonCaller.call(chatClient, "TestAgent", "system", "user", Sample.class);

        assertThat(result.value()).isEqualTo("recovered");
        // First call with the plain system prompt, second with the stricter suffix appended.
        verify(requestSpec).system("system");
        verify(requestSpec).system("system\n\nIMPORTANT: Output ONLY valid JSON. No other text.");
    }

    @Test
    void throwsLlmParseExceptionWhenBothAttemptsFail() {
        when(callResponseSpec.content()).thenReturn("still not json").thenReturn("also not json");

        assertThatThrownBy(() -> llmJsonCaller.call(chatClient, "TestAgent", "system", "user", Sample.class))
            .isInstanceOf(LlmParseException.class)
            .satisfies(ex -> {
                LlmParseException parseException = (LlmParseException) ex;
                assertThat(parseException.getAgentName()).isEqualTo("TestAgent");
                assertThat(parseException.getRawResponse()).isEqualTo("also not json");
            });

        verify(callResponseSpec, times(2)).content();
    }

    @Test
    void callForTextReturnsRawContentWithFencesStripped() {
        when(callResponseSpec.content()).thenReturn("```typescript\nconst x = 1;\n```");

        String result = llmJsonCaller.callForText(chatClient, "TestAgent", "system", "user");

        assertThat(result).isEqualTo("const x = 1;");
    }
}
