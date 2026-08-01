package com.qaforge.domain.exception;

/**
 * Thrown when an agent's LLM response cannot be parsed as valid JSON after the retry
 * described in PRD §10.3. Maps to HTTP 502 LLM_PARSE_ERROR.
 */
public class LlmParseException extends QaForgeException {

    private final String agentName;
    private final String rawResponse;

    public LlmParseException(String agentName, String rawResponse) {
        super("Agent " + agentName + " returned invalid JSON");
        this.agentName = agentName;
        this.rawResponse = rawResponse;
    }

    public LlmParseException(String agentName, String rawResponse, Throwable cause) {
        super("Agent " + agentName + " returned invalid JSON", cause);
        this.agentName = agentName;
        this.rawResponse = rawResponse;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
