package com.qaforge.bootstrap.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Only relevant when a deployer has added a second Spring AI model starter alongside the
 * default one, so multiple named {@link ChatModel} beans exist to route between (PRD §7.5) —
 * e.g. a cheap model for planning, a frontier model for generation. Disabled by default; the
 * bean names referenced by {@code @Qualifier} below ({@code openAiChatModel},
 * {@code anthropicChatModel}) are the ones Spring AI's respective autoconfigurations register.
 */
@Configuration
@ConditionalOnProperty(name = "qaforge.ai.multi-provider.enabled", havingValue = "true")
public class MultiProviderLlmConfig {

    @Bean("planningChatClient")
    public ChatClient planningChatClient(@Qualifier("openAiChatModel") ChatModel model) {
        return ChatClient.builder(model).build();
    }

    @Bean("generationChatClient")
    public ChatClient generationChatClient(@Qualifier("anthropicChatModel") ChatModel model) {
        return ChatClient.builder(model).build();
    }
}
