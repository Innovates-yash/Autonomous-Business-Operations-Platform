package com.aisa.chat.service;

/**
 * Thrown when a chat message fails content validation (Requirements 5.2).
 * Preserves the user's submitted content so it can be returned in the error
 * response — the user doesn't lose their text.
 */
public class InvalidMessageException extends RuntimeException {

    private final String rejectedContent;

    public InvalidMessageException(String message, String rejectedContent) {
        super(message);
        this.rejectedContent = rejectedContent;
    }

    /**
     * Returns the user's original message content that was rejected.
     */
    public String getRejectedContent() {
        return rejectedContent;
    }
}
