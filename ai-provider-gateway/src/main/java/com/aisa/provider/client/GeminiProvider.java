package com.aisa.provider.client;

import com.aisa.provider.model.ProviderType;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Gemini client behind the uniform contract (Requirement 20.1). Created only when a Gemini
 * (Vertex AI) {@link ChatModel} bean is present (see
 * {@link com.aisa.provider.config.ProviderClientConfiguration}).
 */
public class GeminiProvider extends SpringAiProvider {

    public GeminiProvider(ChatModel chatModel) {
        super(chatModel);
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.GEMINI;
    }
}
