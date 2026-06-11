package com.aisa.chat.domain;

/**
 * The role associated with a chat message (Requirement 5.9). Either the
 * human user submitting a message or the AI assistant generating a response.
 */
public enum MessageRole {
    USER,
    ASSISTANT
}
