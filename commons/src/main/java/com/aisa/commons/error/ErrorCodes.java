package com.aisa.commons.error;

/**
 * Stable error codes shared across services. Codes are part of the API contract and
 * must remain stable across releases.
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    // Authentication / authorization
    public static final String AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
    public static final String DUPLICATE_ACCOUNT = "DUPLICATE_ACCOUNT";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String AUTHORIZATION_DENIED = "AUTHORIZATION_DENIED";

    // Validation
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";

    // Resources / state
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION";

    // Rate limiting
    public static final String RATE_LIMITED = "RATE_LIMITED";

    // Transport security
    public static final String ENCRYPTED_TRANSPORT_REQUIRED = "ENCRYPTED_TRANSPORT_REQUIRED";

    // AI provider / orchestration
    public static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    public static final String AGENT_FAILED = "AGENT_FAILED";
    public static final String GENERATION_FAILED = "GENERATION_FAILED";

    // Generic
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
