package com.aisa.provider.model;

/**
 * The set of AI providers the gateway can route to (Requirement 20.1).
 *
 * <p>The supported provider set defined by the AI_Stack is OpenAI, Gemini, Claude, and a
 * Local LLM. Selection of any value outside a configured provider is rejected at selection
 * time (Requirement 20.3); the enum itself enumerates the universe of routable providers.
 */
public enum ProviderType {
    OPENAI,
    GEMINI,
    CLAUDE,
    LOCAL_LLM
}
