package com.aisa.provider.contract;

import java.util.List;
import java.util.Objects;

/**
 * The single, provider-agnostic request presented to every AI provider (Requirement 20.4).
 *
 * <p>All agents and the chat service construct this same structure regardless of which
 * provider is currently selected. Provider clients (task 7.2) adapt it to their underlying
 * Spring AI model. The request never carries provider-specific fields, which is what
 * guarantees the uniform contract.
 *
 * @param messages the ordered conversation; must contain at least one message
 * @param options  generation options; never {@code null} (use {@link UniformOptions#defaults()})
 */
public record UniformRequest(List<UniformMessage> messages, UniformOptions options) {

    public UniformRequest {
        Objects.requireNonNull(messages, "messages");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must contain at least one message");
        }
        messages = List.copyOf(messages);
        options = options == null ? UniformOptions.defaults() : options;
    }

    /** Convenience factory for a single-prompt request using default options. */
    public static UniformRequest ofPrompt(String prompt) {
        return new UniformRequest(List.of(UniformMessage.user(prompt)), UniformOptions.defaults());
    }
}
