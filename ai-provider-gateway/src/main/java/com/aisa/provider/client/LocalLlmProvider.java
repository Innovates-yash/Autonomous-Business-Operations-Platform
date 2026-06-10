package com.aisa.provider.client;

import com.aisa.provider.model.ProviderType;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Local LLM client behind the uniform contract (Requirement 20.1). Backed by any self-hosted
 * OpenAI-compatible {@link ChatModel} (e.g. Ollama). Created only when a local {@link ChatModel}
 * bean is present (see {@link com.aisa.provider.config.ProviderClientConfiguration}).
 */
public class LocalLlmProvider extends SpringAiProvider {

    public LocalLlmProvider(ChatModel chatModel) {
        super(chatModel);
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.LOCAL_LLM;
    }
}
