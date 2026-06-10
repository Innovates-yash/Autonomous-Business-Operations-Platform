package com.aisa.provider.config;

import com.aisa.provider.client.ClaudeProvider;
import com.aisa.provider.client.GeminiProvider;
import com.aisa.provider.client.LocalLlmProvider;
import com.aisa.provider.client.OpenAiProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the real Spring AI provider clients (Requirement 20.1/20.4).
 *
 * <p>Each client is created only when the corresponding Spring AI {@link ChatModel} bean is on
 * the context, identified by the conventional bean name a provider starter contributes
 * ({@code openAiChatModel}, {@code vertexAiGeminiChat}, {@code anthropicChatModel}, and a
 * locally configured {@code localChatModel}). Because no provider starter is on the classpath
 * by default, these beans are absent in dev/test and the deterministic stubs from
 * {@link StubProviderConfiguration} serve instead. Adding a starter and its credentials is a
 * pure configuration change — no caller code changes (Req 20.4).
 */
@Configuration
public class ProviderClientConfiguration {

    @Bean
    @ConditionalOnBean(name = "openAiChatModel")
    public OpenAiProvider openAiProvider(@Qualifier("openAiChatModel") ChatModel chatModel) {
        return new OpenAiProvider(chatModel);
    }

    @Bean
    @ConditionalOnBean(name = "vertexAiGeminiChat")
    public GeminiProvider geminiProvider(@Qualifier("vertexAiGeminiChat") ChatModel chatModel) {
        return new GeminiProvider(chatModel);
    }

    @Bean
    @ConditionalOnBean(name = "anthropicChatModel")
    public ClaudeProvider claudeProvider(@Qualifier("anthropicChatModel") ChatModel chatModel) {
        return new ClaudeProvider(chatModel);
    }

    @Bean
    @ConditionalOnBean(name = "localChatModel")
    public LocalLlmProvider localLlmProvider(@Qualifier("localChatModel") ChatModel chatModel) {
        return new LocalLlmProvider(chatModel);
    }
}
