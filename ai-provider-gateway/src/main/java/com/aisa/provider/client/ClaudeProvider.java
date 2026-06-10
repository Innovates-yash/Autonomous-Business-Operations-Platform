package com.aisa.provider.client;

import com.aisa.provider.model.ProviderType;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Claude (Anthropic) client behind the uniform contract (Requirement 20.1). Created only when
 * an Anthropic {@link ChatModel} bean is present (see
 * {@link com.aisa.provider.config.ProviderClientConfiguration}).
 */
public class ClaudeProvider extends SpringAiProvider {

    public ClaudeProvider(ChatModel chatModel) {
        super(chatModel);
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.CLAUDE;
    }
}
