package com.aisa.auth.web;

/**
 * Thrown when an endpoint receives a role name that cannot be mapped to a valid
 * {@link com.aisa.auth.domain.RoleName} enum value.
 */
public class InvalidRoleNameException extends RuntimeException {

    public InvalidRoleNameException(String message) {
        super(message);
    }
}
