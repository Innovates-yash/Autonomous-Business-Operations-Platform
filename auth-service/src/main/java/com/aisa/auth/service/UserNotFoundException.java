package com.aisa.auth.service;

/**
 * Thrown when an operation references a user that does not exist in the system.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
