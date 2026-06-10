package com.aisa.auth.service;

/**
 * Raised when a presented refresh token is unrecognized, expired, already used, or
 * revoked (Requirement 1.7). The request is rejected with an authentication error.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token");
    }
}
