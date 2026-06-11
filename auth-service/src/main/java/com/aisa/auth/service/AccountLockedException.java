package com.aisa.auth.service;

/**
 * Raised when a login attempt targets an account that is currently in a locked
 * state (Requirement 1.14). The credentials are NOT evaluated; the request is
 * rejected immediately.
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        super("Account is temporarily locked due to too many failed login attempts");
    }
}
