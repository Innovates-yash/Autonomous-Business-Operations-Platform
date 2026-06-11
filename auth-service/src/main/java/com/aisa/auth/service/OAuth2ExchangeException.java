package com.aisa.auth.service;

/**
 * Thrown when the OAuth2 authorization-code exchange fails or is denied by the
 * provider (Requirement 1.13). When this exception is raised, no Platform tokens
 * are issued.
 */
public class OAuth2ExchangeException extends RuntimeException {

    public OAuth2ExchangeException(String message) {
        super(message);
    }

    public OAuth2ExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
