package com.aisa.auth.service;

/**
 * Raised when login credentials are invalid (Requirement 1.9). The message is
 * deliberately uniform and never reveals whether the email was unknown or the
 * password was wrong, so callers cannot probe for valid accounts.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
