package com.aisa.auth.service;

/**
 * Raised when registration is attempted with an email that already has an
 * account. The registration is rejected and no new account is created
 * (Requirement 1.2).
 */
public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String message) {
        super(message);
    }
}
