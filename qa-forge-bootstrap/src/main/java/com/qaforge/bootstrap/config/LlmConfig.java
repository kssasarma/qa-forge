package com.qaforge.bootstrap.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Builds the primary {@link ChatClient} from whichever {@link ChatModel} is on the classpath
 * (PRD §7.4). This is the only place a vendor-specific Spring AI type may be referenced
 * indirectly (via the auto-configured {@code ChatModel} bean) — every agent downstream only
 * ever sees the vendor-neutral {@code ChatClient}.
 *
 * <p>Temperature is fixed at 0.0 for deterministic, parseable JSON output; agents that need
 * creativity can override per-call via {@code ChatOptions} on their own prompt.
 */
@Configuration
public class LlmConfig {

    @Bean
    @Primary
    public ChatClient primaryChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultOptions(ChatOptions.builder().temperature(0.0))
            .build();
    }
}
