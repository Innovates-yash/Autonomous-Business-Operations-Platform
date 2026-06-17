package com.aisa.chat.domain;

/**
 * The role associated with a chat message (Requirement 5.9). Represents the
 * human user submitting a message, the AI assistant generating a response,
 * or a system prompt injected for context.
 */
public enum MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
