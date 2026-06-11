package com.aisa.audit.query;

/**
 * Raised when a caller that does not hold the Admin role attempts to query audit
 * events. The Audit_Service denies the request and returns an insufficient-
 * authorization error (Req 23.6) without evaluating or returning any events.
 */
public class AuthorizationDeniedException extends RuntimeException {

    public AuthorizationDeniedException(String message) {
        super(message);
    }
}
