package com.qaforge.application.agent.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaforge.domain.exception.LlmParseException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Shared JSON-call-and-parse policy for every agent, per PRD §10.3 and §16.2:
 *
 * <pre>
 * attempt 1 → call LLM → try parse JSON
 *     if fails → append "\n\nIMPORTANT: Output ONLY valid JSON. No other text."
 * attempt 2 → call LLM → try parse JSON
 *     if fails → throw LlmParseException(agentName, rawResponse)
 * </pre>
 *
 * <p>Owns a private Jackson 2 {@link ObjectMapper} instance rather than accepting one via
 * dependency injection: Spring Boot 4.1.0 runs Jackson 3 ({@code tools.jackson.*}) as its
 * primary JSON stack (see {@code spring-boot-starter-jackson}), so relying on Spring to
 * inject a {@code com.fasterxml.jackson.databind.ObjectMapper} bean here would be fragile —
 * this class's parsing needs (agent JSON responses) are unrelated to the web layer's
 * serialization config anyway.
 */
@Component
public class LlmJsonCaller {

    private static final Logger log = LoggerFactory.getLogger(LlmJsonCaller.class);
    private static final Pattern LEADING_FENCE = Pattern.compile("(?s)^```[a-zA-Z]*\\n?");
    private static final Pattern TRAILING_FENCE = Pattern.compile("\\n?```$");
    private static final String STRICT_JSON_SUFFIX =
        "\n\nIMPORTANT: Output ONLY valid JSON. No other text.";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MeterRegistry meterRegistry;

    public LlmJsonCaller(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** Serializes a value to JSON for inclusion in a user prompt. */
    public String toJson(Object value, String agentName) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new LlmParseException(agentName, "serialization failure", e);
        }
    }

    /**
     * Calls the LLM with the given system/user prompts and parses the response as {@code type}.
     * Retries once with a stricter instruction on parse failure; throws
     * {@link LlmParseException} if both attempts fail to parse.
     */
    public <T> T call(ChatClient chatClient, String agentName, String systemPrompt,
                       String userMessage, Class<T> type) {
        String rawFirst = invoke(chatClient, agentName, systemPrompt, userMessage);
        try {
            T result = parse(rawFirst, type);
            recordOutcome(agentName, "success");
            return result;
        } catch (JsonProcessingException firstFailure) {
            log.warn("Agent {} produced unparseable JSON on attempt 1; retrying with a stricter prompt", agentName);
            meterRegistry.counter("qaforge.llm.retries", "agent", agentName).increment();

            String rawSecond = invoke(chatClient, agentName, systemPrompt + STRICT_JSON_SUFFIX, userMessage);
            try {
                T result = parse(rawSecond, type);
                recordOutcome(agentName, "success");
                return result;
            } catch (JsonProcessingException secondFailure) {
                recordOutcome(agentName, "parse_error");
                throw new LlmParseException(agentName, rawSecond, secondFailure);
            }
        }
    }

    /** Calls the LLM and returns the raw text response, with no JSON parsing. */
    public String callForText(ChatClient chatClient, String agentName, String systemPrompt, String userMessage) {
        String raw = invoke(chatClient, agentName, systemPrompt, userMessage);
        recordOutcome(agentName, "success");
        return stripCodeFences(raw);
    }

    private String invoke(ChatClient chatClient, String agentName, String systemPrompt, String userMessage) {
        try {
            return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
        } catch (RuntimeException e) {
            recordOutcome(agentName, "call_error");
            throw e;
        }
    }

    private <T> T parse(String rawResponse, Class<T> type) throws JsonProcessingException {
        return objectMapper.readValue(stripCodeFences(rawResponse), type);
    }

    private String stripCodeFences(String response) {
        String cleaned = LEADING_FENCE.matcher(response.strip()).replaceFirst("");
        cleaned = TRAILING_FENCE.matcher(cleaned).replaceFirst("");
        return cleaned.strip();
    }

    private void recordOutcome(String agentName, String outcome) {
        Counter.builder("qaforge.llm.calls")
            .tag("agent", agentName)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();
    }
}
