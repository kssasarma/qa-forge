package com.qaforge.bootstrap.it;

import com.qaforge.application.prompt.CodeAnalysisPrompts;
import com.qaforge.application.prompt.ContextGatheringPrompts;
import com.qaforge.application.prompt.ExecutionPrompts;
import com.qaforge.application.prompt.PlaywrightGenerationPrompts;
import com.qaforge.application.prompt.TestPlanningPrompts;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * PRD §19.4's "mock ChatModel that returns canned JSON (no real LLM calls)", keyed by which
 * agent's system prompt is present on the incoming {@link Prompt} — each agent in the pipeline
 * uses its own fixed system prompt constant, so exact-matching the system message text against
 * those constants is enough to route to the right canned response without ever inspecting the
 * (LLM-generated, hence unpredictable) user message content.
 */
class CannedResponseChatModel implements ChatModel {

    private final Map<String, String> responsesBySystemPrompt = Map.of(
        ContextGatheringPrompts.SYSTEM, """
            {
              "prSummary": "Adds a confirmation dialog before checkout completes.",
              "changedAreas": ["checkout"],
              "acceptanceCriteriaLines": ["User sees a confirmation dialog before checkout completes"],
              "reviewerConcerns": [],
              "estimatedRisk": "MEDIUM"
            }
            """,
        CodeAnalysisPrompts.SYSTEM, """
            {
              "impactedUserFlows": [{"flow": "Checkout", "reason": "New confirmation dialog added", "severity": "MEDIUM"}],
              "safeToSkipFlows": [],
              "backendOnlyChange": false,
              "uiPagesAffected": ["/checkout"],
              "newApiEndpoints": [],
              "changedDbTables": []
            }
            """,
        TestPlanningPrompts.SYSTEM, """
            {
              "scenarios": [
                {
                  "id": "scenario-1",
                  "title": "Checkout confirmation dialog appears",
                  "description": "Verify the confirmation dialog appears before checkout completes",
                  "userFlow": "Checkout",
                  "type": "NEW",
                  "layer": "PLAYWRIGHT",
                  "steps": ["Add item to cart", "Proceed to checkout", "Assert confirmation dialog visible"],
                  "targetPath": "/checkout",
                  "requiresAuth": false,
                  "openApiOperationId": null,
                  "dbTable": null
                }
              ]
            }
            """,
        PlaywrightGenerationPrompts.SYSTEM, """
            import { test, expect } from '@playwright/test';

            test('Checkout confirmation dialog appears', async ({ page }) => {
              await page.goto('/checkout');
              await page.getByRole('button', { name: 'Checkout' }).click();
              await expect(page.getByRole('dialog')).toBeVisible();
            });
            """,
        ExecutionPrompts.EXECUTE_SYSTEM, """
            {
              "results": [
                {
                  "scenarioId": "scenario-1",
                  "fileName": "checkout_confirmation_dialog_appears_pr42.spec.ts",
                  "status": "PASSED",
                  "errorMessage": null,
                  "durationMs": 1200,
                  "retryCount": 0
                }
              ]
            }
            """
    );

    @Override
    public ChatResponse call(Prompt prompt) {
        String systemText = prompt.getInstructions().stream()
            .filter(m -> m.getMessageType() == MessageType.SYSTEM)
            .findFirst()
            .map(Message::getText)
            .orElse("");

        String canned = responsesBySystemPrompt.get(systemText);
        if (canned == null) {
            throw new IllegalStateException(
                "CannedResponseChatModel has no stubbed response for system prompt starting with: "
                    + systemText.substring(0, Math.min(80, systemText.length())));
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(canned))));
    }
}
