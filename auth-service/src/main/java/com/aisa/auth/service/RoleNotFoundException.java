package com.aisa.auth.service;

/**
 * Thrown when a role assignment references a role that does not exist in the system.
 */
public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(String message) {
        super(message);
    }
}
