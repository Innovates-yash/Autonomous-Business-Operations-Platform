package com.aisa.auth.service;

/**
 * Thrown when a non-Admin user attempts an operation restricted to the Admin role
 * (Requirement 2.7).
 */
public class AdminOnlyOperationException extends RuntimeException {

    public AdminOnlyOperationException(String message) {
        super(message);
    }
}
