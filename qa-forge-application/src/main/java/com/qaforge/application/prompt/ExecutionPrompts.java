package com.qaforge.application.prompt;

/**
 * Prompts for {@code TestExecutionAgent} and {@code SelfHealingLocatorAgent}.
 *
 * <p>{@link #SELF_HEAL_SYSTEM} is verbatim from PRD §9.2.9. PRD §9.2.8 describes
 * {@code TestExecutionAgent}'s responsibility but does not give a literal system prompt
 * block (unlike the other agents) — {@link #EXECUTE_SYSTEM} is authored here to match that
 * responsibility exactly: drive the Playwright MCP tools and report back the same JSON shape
 * as {@code ExecutionResult}.
 */
public final class ExecutionPrompts {

    public static final String EXECUTE_SYSTEM = """
        You are a Playwright test execution agent. You are given one or more Playwright
        TypeScript test files (file name, tags, and full source) and a target base URL.

        For each test, use the available Playwright MCP browser tools to navigate the target
        application and carry out the steps implied by the test source, then verify its
        assertions using the accessibility tree (browser_snapshot) rather than screenshots.

        After executing all tests, produce exactly this JSON and nothing else:
        {
          "results": [
            {
              "scenarioId": "<scenario id parsed from the file's '@qa-forge generated' header comment>",
              "fileName": "<file name>",
              "status": "PASSED | FAILED | SKIPPED | ERROR",
              "errorMessage": "<null when PASSED; otherwise a concise description>",
              "durationMs": <integer milliseconds spent on this test>,
              "retryCount": 0
            }
          ]
        }

        Rules:
        - Output ONLY the JSON object. No markdown fences. No preamble.
        - status = FAILED when an assertion did not hold; status = ERROR when a tool call
          failed, timed out, or the page could not be reached; status = SKIPPED when the test
          could not be attempted at all.
        - errorMessage must mention "Element not found" or "strict mode violation" verbatim
          when a locator could not be resolved, so failures can be routed to self-healing.
        - Never fabricate a PASSED result without having actually driven the browser tools.
        """;

    public static final String SELF_HEAL_SYSTEM = """
        You are a Playwright expert. A test failed because a locator was not found.
        You receive: the failing test TypeScript source, the error message, and the page's
        accessibility tree snapshot (from the browser_snapshot tool).

        Your task:
        1. Use browser_snapshot to get the current accessibility tree.
        2. Identify alternative locators from the accessibility tree that match the intent.
        3. Rewrite ONLY the failing locator line with the best alternative.
        4. Produce the corrected TypeScript source.
        5. Output the corrected source only. No markdown fences.
        """;

    private ExecutionPrompts() {}
}
