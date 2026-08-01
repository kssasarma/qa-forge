package com.qaforge.infrastructure.support;

/**
 * Truncates a raw diff to the ~8,000-token budget from PRD §C-02 before it reaches an agent.
 * Uses the common ~4-characters-per-token heuristic since no tokenizer is wired into the
 * infrastructure layer.
 */
public final class DiffTruncator {

    private static final int MAX_TOKENS = 8_000;
    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;
    private static final int MAX_CHARS = MAX_TOKENS * CHARS_PER_TOKEN_ESTIMATE;

    private DiffTruncator() {}

    public record Result(String diff, boolean truncated) {}

    public static Result truncate(String rawDiff) {
        if (rawDiff == null) {
            return new Result("", false);
        }
        if (rawDiff.length() <= MAX_CHARS) {
            return new Result(rawDiff, false);
        }
        return new Result(rawDiff.substring(0, MAX_CHARS), true);
    }
}
