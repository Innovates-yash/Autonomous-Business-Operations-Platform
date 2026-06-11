package com.aisa.auth.web.dto;

/**
 * Response payload for the authorization decision endpoint (POST /api/auth/authorize).
 * Returns a PERMIT or DENY decision (Requirement 2.3, 2.4).
 *
 * @param decision either "PERMIT" or "DENY"
 */
public record AuthorizeResponse(String decision) {

    public static final String PERMIT = "PERMIT";
    public static final String DENY = "DENY";

    public static AuthorizeResponse permit() {
        return new AuthorizeResponse(PERMIT);
    }

    public static AuthorizeResponse deny() {
        return new AuthorizeResponse(DENY);
    }
}
