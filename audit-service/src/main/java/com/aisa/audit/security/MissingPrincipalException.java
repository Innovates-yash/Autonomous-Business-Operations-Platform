package com.aisa.audit.security;

/**
 * Raised when an audit query request reaches the Audit_Service without a usable
 * authenticated principal in the forwarded {@code X-User-Id} header. In normal
 * operation the API Gateway guarantees this header; this exception guards the
 * service when it is called directly or with a malformed identity.
 */
public class MissingPrincipalException extends RuntimeException {

    public MissingPrincipalException(String message) {
        super(message);
    }
}
